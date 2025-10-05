package net.pablo.rpgclasses.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEffects {

    // Deferred register for all effects
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "rpgclasses");

    // Register the Phantom effect
    public static final RegistryObject<MobEffect> PHANTOM =
            EFFECTS.register("phantom", PhantomEffect::new);

    public static final RegistryObject<MobEffect> CHAINED =
            EFFECTS.register("chained", ChainedEffect::new);

}
