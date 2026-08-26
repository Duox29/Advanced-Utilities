package com.duox.advancedutilities.mixin;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Unpacks the (package-private) PalettedContainer.Data record components. */
@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer$Data")
public interface IPalettedContainerDataAccessor {

    @Accessor("storage")
    BitStorage getStorage();

    @Accessor("palette")
    Palette<?> getPalette();
}
