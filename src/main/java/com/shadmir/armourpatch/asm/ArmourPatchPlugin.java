package com.shadmir.armourpatch.asm;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("ArmourPatchPlugin")
public class ArmourPatchPlugin implements IFMLLoadingPlugin {

    // Separate logger — do NOT use ArmourPatch.LOG here; the main mod class is not loaded yet.
    static final Logger LOG = LogManager.getLogger("ArmourPatchPlugin");

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.shadmir.armourpatch.asm.ArmourPatchTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
