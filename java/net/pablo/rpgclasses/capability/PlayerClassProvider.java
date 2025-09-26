package net.pablo.rpgclasses.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.pablo.rpgclasses.registry.RPGClassRegistry;
import net.pablo.rpgclasses.RpgClassesMod;

public class PlayerClassProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IPlayerClass> PLAYER_CLASS_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation PLAYER_CLASS_ID =
            new ResourceLocation(RpgClassesMod.MOD_ID, "player_class");

    private final IPlayerClass instance = new PlayerClass();
    private final LazyOptional<IPlayerClass> optional = LazyOptional.of(() -> instance);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == PLAYER_CLASS_CAPABILITY ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        if (instance.getSelectedClass() != null)
            tag.putString("SelectedClass", instance.getSelectedClass().getClassName());
        if (instance.getSecondaryClass() != null)
            tag.putString("SecondaryClass", instance.getSecondaryClass().getClassName());

        tag.putString("PreviousClassName", instance.getPreviousClassName() == null ? "" : instance.getPreviousClassName());
        tag.putString("PreviousSecondaryClassName", instance.getPreviousSecondaryClassName() == null ? "" : instance.getPreviousSecondaryClassName());

        CompoundTag xpTag = new CompoundTag();
        for (var entry : instance.getClassXPMap().entrySet()) xpTag.putInt(entry.getKey(), entry.getValue());
        tag.put("ClassXP", xpTag);

        CompoundTag levelTag = new CompoundTag();
        for (var entry : instance.getClassLevelMap().entrySet()) levelTag.putInt(entry.getKey(), entry.getValue());
        tag.put("ClassLevel", levelTag);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("SelectedClass"))
            instance.setSelectedClass(RPGClassRegistry.getClassByName(nbt.getString("SelectedClass")));
        if (nbt.contains("SecondaryClass"))
            instance.setSecondaryClass(RPGClassRegistry.getClassByName(nbt.getString("SecondaryClass")));

        instance.setPreviousClassName(nbt.getString("PreviousClassName"));
        instance.setPreviousSecondaryClassName(nbt.getString("PreviousSecondaryClassName"));

        if (nbt.contains("ClassXP")) {
            CompoundTag xpTag = nbt.getCompound("ClassXP");
            for (String key : xpTag.getAllKeys()) instance.addXP(key, xpTag.getInt(key));
        }

        if (nbt.contains("ClassLevel")) {
            CompoundTag levelTag = nbt.getCompound("ClassLevel");
            for (String key : levelTag.getAllKeys()) instance.setLevel(key, levelTag.getInt(key));
        }
    }
}
