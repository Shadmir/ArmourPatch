package com.shadmir.armourpatch.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ArmourPatchTransformer implements IClassTransformer {

    // Forge class — never obfuscated regardless of environment.
    private static final String TARGET_CLASS = "net.minecraftforge.common.ISpecialArmor$ArmorProperties";
    private static final String TARGET_INTERNAL = "net/minecraftforge/common/ISpecialArmor$ArmorProperties";
    private static final String TARGET_METHOD = "ApplyArmor";
    // The 5-argument overload ends with (double damage, boolean applyDamage) -> float.
    // Primitive types are never obfuscated, so this suffix is stable.
    private static final String DESC_SUFFIX = "DZ)F";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TARGET_CLASS.equals(transformedName)) return basicClass;
        ArmourPatchPlugin.LOG.info("[ArmourPatch] Transforming {}", transformedName);
        try {
            return transformClass(basicClass);
        } catch (Exception e) {
            ArmourPatchPlugin.LOG.error("[ArmourPatch] Transformation failed — returning original class.", e);
            return basicClass;
        }
    }

    private byte[] transformClass(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean patched = false;
        for (MethodNode method : classNode.methods) {
            if (TARGET_METHOD.equals(method.name) && method.desc.endsWith(DESC_SUFFIX)) {
                patched = transformMethod(method);
                break;
            }
        }

        if (!patched) {
            ArmourPatchPlugin.LOG
                .warn("[ArmourPatch] Could not find patch site in {}. Armor fix was NOT applied.", TARGET_CLASS);
            return basicClass;
        }

        // COMPUTE_MAXS recalculates max stack and locals without triggering class loading
        // (unlike COMPUTE_FRAMES, which calls getCommonSuperClass and can cause ClassCircularityError).
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        ArmourPatchPlugin.LOG.info("[ArmourPatch] Successfully patched armor AbsorbMax.");
        return writer.toByteArray();
    }

    /**
     * Finds and replaces the durability-based AbsorbMax calculation in ApplyArmor.
     *
     * The buggy line in source:
     * new ArmorProperties(0, (double)armor.damageReduceAmount / 25.0D,
     * armor.getMaxDamage() + 1 - stack.getItemDamage())
     *
     * Bytecode pattern immediately before INVOKESPECIAL ArmorProperties.<init>(IDI)V,
     * listed closest-to-anchor first:
     * real[0] ISUB
     * real[1] INVOKEVIRTUAL getItemDamage ()I
     * real[2] ALOAD (stack local var)
     * real[3] IADD
     * real[4] ICONST_1
     * real[5] INVOKEVIRTUAL getMaxDamage ()I
     * real[6] ALOAD (armor local var)
     *
     * Replacement: GETSTATIC java/lang/Integer.MAX_VALUE I
     *
     * Matching is opcode-only for ALOAD/INVOKEVIRTUAL (obfuscation-safe).
     */
    private boolean transformMethod(MethodNode method) {
        InsnList insns = method.instructions;
        AbstractInsnNode[] arr = insns.toArray();

        // First pass: log every INVOKESPECIAL targeting ArmorProperties so we can see the actual descriptor.
        for (AbstractInsnNode node : arr) {
            if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode min = (MethodInsnNode) node;
                if (TARGET_INTERNAL.equals(min.owner)) {
                    ArmourPatchPlugin.LOG
                        .info("[ArmourPatch] Found INVOKESPECIAL {}.{}{}", min.owner, min.name, min.desc);
                }
            }
        }

        for (int i = arr.length - 1; i >= 7; i--) {
            AbstractInsnNode anchor = arr[i];

            // Anchor: INVOKESPECIAL ArmorProperties.<init>(IDI)V
            if (anchor.getOpcode() != Opcodes.INVOKESPECIAL) continue;
            MethodInsnNode min = (MethodInsnNode) anchor;
            if (!TARGET_INTERNAL.equals(min.owner)) continue;
            if (!"<init>".equals(min.name)) continue;
            if (!"(IDI)V".equals(min.desc)) continue;

            // Collect the 7 real instructions immediately preceding the anchor.
            AbstractInsnNode[] real = collectPreceding(arr, i, 7);
            if (real == null) continue;

            // Log what we actually see before the constructor call.
            for (int k = real.length - 1; k >= 0; k--) {
                AbstractInsnNode n = real[k];
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode mn = (MethodInsnNode) n;
                    ArmourPatchPlugin.LOG
                        .info("[ArmourPatch] real[{}] opcode={} {}.{}{}", k, n.getOpcode(), mn.owner, mn.name, mn.desc);
                } else {
                    ArmourPatchPlugin.LOG.info("[ArmourPatch] real[{}] opcode={}", k, n.getOpcode());
                }
            }

            // Validate the pattern.
            if (real[0].getOpcode() != Opcodes.ISUB) continue;
            if (!isInvokeVirtual(real[1])) continue; // getItemDamage()
            if (!isAload(real[2])) continue;
            if (real[3].getOpcode() != Opcodes.IADD) continue;
            if (real[4].getOpcode() != Opcodes.ICONST_1) continue;
            if (!isInvokeVirtual(real[5])) continue; // getMaxDamage()
            if (!isAload(real[6])) continue;

            // Pattern confirmed. Insert Integer.MAX_VALUE before real[0], then remove real[0..6].
            insns.insertBefore(real[0], new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Integer", "MAX_VALUE", "I"));
            for (AbstractInsnNode node : real) insns.remove(node);

            ArmourPatchPlugin.LOG
                .info("[ArmourPatch] Replaced AbsorbMax durability calculation with Integer.MAX_VALUE.");
            return true;
        }
        return false;
    }

    /**
     * Collects {@code count} non-pseudo instructions (opcode != -1) immediately before
     * {@code arr[anchorIdx]}, returning them closest-to-anchor first.
     * Returns null if fewer than {@code count} real instructions exist before the anchor.
     */
    private AbstractInsnNode[] collectPreceding(AbstractInsnNode[] arr, int anchorIdx, int count) {
        AbstractInsnNode[] result = new AbstractInsnNode[count];
        int filled = 0;
        for (int j = anchorIdx - 1; j >= 0 && filled < count; j--) {
            if (arr[j].getOpcode() != -1) result[filled++] = arr[j];
        }
        return filled == count ? result : null;
    }

    // In ASM's tree API all aload_N raw opcodes are normalised to VarInsnNode(Opcodes.ALOAD, N).
    private boolean isAload(AbstractInsnNode node) {
        return node.getOpcode() == Opcodes.ALOAD;
    }

    // Match INVOKEVIRTUAL by return/param descriptor only — method name is obfuscated.
    private boolean isInvokeVirtual(AbstractInsnNode node) {
        if (node.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;
        return "()I".equals(((MethodInsnNode) node).desc);
    }
}
