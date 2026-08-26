package com.duox.advancedutilities.mixin;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the internal data (bit storage + palette holder) of a PalettedContainer so
 * the Finder scanner can iterate raw palette indices instead of doing a virtual
 * getBlockState dispatch per cell.
 *
 * {@link net.minecraft.world.level.chunk.PalettedContainer.Data} is package-private,
 * so the field is surfaced as Object and unpacked via
 * {@link IPalettedContainerDataAccessor}.
 */
@Mixin(PalettedContainer.class)
public interface IPalettedContainerAccessor<T> {

    @Accessor("data")
    Object getData();
}
