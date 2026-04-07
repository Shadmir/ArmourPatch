package com.shadmir.armourpatch;

import java.util.Random;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    private static final String[] STARTUP_LINES = { "Your chestplate thanks you for the upgrade.",
        "Armour durability is now merely cosmetic.", "Your iron boots protect at 1 HP just as well as at 100.",
        "No more dying because you forgot to repair at the blacksmith.", "Armour patch applied. The dwarves approve.",
        "Full protection, no matter the dents.", "Forged in fire. Patched in Java.",
        "Your armour works harder than you do.", "One does not simply lose protection from low durability.",
        "Even a cracked shield still shields.", "Hi Mido!" };

    public void preInit(FMLPreInitializationEvent event) {
        ArmourPatch.LOG.info("ArmourPatch {} loaded.", Tags.VERSION);
        ArmourPatch.LOG.info("[ArmourPatch] {}", STARTUP_LINES[new Random().nextInt(STARTUP_LINES.length)]);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
