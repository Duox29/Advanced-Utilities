package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.BlockListSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BlockSelector {

    // Instance duy nhất để quản lý
    public static final BlockSelector INSTANCE = new BlockSelector();

    private boolean isActive = false;
    private BlockListSetting currentSetting = null;

    // Đăng ký Event Bus
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void startSelecting(BlockListSetting setting) {
        this.currentSetting = setting;
        this.isActive = true;

        // Đóng GUI để người dùng nhìn thấy game
        Minecraft.getInstance().setScreen(null);

        // Thông báo hướng dẫn
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("Right-click a block to add it to the list!").withStyle(ChatFormatting.YELLOW), true
            );
        }
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.InteractionKeyMappingTriggered event) {
        // Chỉ xử lý khi đang ở chế độ chọn và là chuột phải (Use Item key)
        if (!isActive || !event.isUseItem()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {

            // Lấy block tại vị trí crosshair
            net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) mc.hitResult;
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());

            // Thêm vào Setting
            if (currentSetting != null) {
                currentSetting.add(state.getBlock());

                // Thông báo thành công
                mc.player.displayClientMessage(
                        Component.literal("Added: " + state.getBlock().getName().getString()).withStyle(ChatFormatting.GREEN), true
                );
            }

            // Reset trạng thái
            isActive = false;
            currentSetting = null;

            // Chặn sự kiện game (không đặt block hay mở rương thật)
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}