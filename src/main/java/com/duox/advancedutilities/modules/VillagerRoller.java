package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting.EnchantmentData;
import com.duox.advancedutilities.system.settings.ItemListSetting;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class VillagerRoller extends Module {

    private final ItemListSetting wantedItems = new ItemListSetting("Wanted Items");
    private final EnchantmentListSetting wantedEnchantments = new EnchantmentListSetting("Enchantments");
    private Villager targetVillager;
    private BlockPos jobBlockPos;
    private Block jobBlock;

    private State currentState = State.IDLE;
    private int tickCounter = 0;

    private boolean selectingVillager = false;
    private boolean selectingBlock = false;

    public VillagerRoller() {
        super("Villager Roller", "Auto-rolls villager trades.", Category.WORLD);
        addSetting(wantedItems);
        addSetting(wantedEnchantments);
    }

    @Override
    public void onEnable() {
        NeoForge.EVENT_BUS.register(this);
        resetRuntimeState();
        startSelection();
    }

    @Override
    public void onDisable() {
        NeoForge.EVENT_BUS.unregister(this);
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        targetVillager = null;
        jobBlockPos = null;
        jobBlock = null;
        selectingVillager = false;
        selectingBlock = false;
        setState(State.IDLE);
    }

    private void startSelection() {
        resetRuntimeState();
        selectingVillager = true;
        sendMessage("§e[VillagerRoller] Step 1: Right-click the Villager.");
    }

    private void sendMessage(String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), false);
        }
    }

    private void stopWithMessage(String text) {
        sendMessage(text);
        toggle();
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!selectingVillager || !(event.getTarget() instanceof Villager villager)) {
            return;
        }

        targetVillager = villager;
        selectingVillager = false;
        selectingBlock = true;

        event.setCanceled(true);
       sendMessage("§e[VillagerRoller] Step 2: Right-click the Job Block.");
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!selectingBlock || mc.level == null) {
            return;
        }

        jobBlockPos = event.getPos();
        jobBlock = resolveSelectedJobBlock(event.getPos(), event.getItemStack());

        selectingBlock = false;
        event.setCanceled(true);

        sendMessage("§a[VillagerRoller] Setup Complete! Starting roll...");
        setState(State.CHECK_VILLAGER);
    }

    private Block resolveSelectedJobBlock(BlockPos pos, ItemStack heldStack) {
        if (!heldStack.isEmpty()) {
            Block heldBlock = Block.byItem(heldStack.getItem());
            if (heldBlock != Blocks.AIR) {
                return heldBlock;
            }
        }
        return mc.level.getBlockState(pos).getBlock();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (selectingVillager || selectingBlock) {
            return;
        }

        if (targetVillager == null || jobBlockPos == null || jobBlock == null || targetVillager.isRemoved()) {
            stopWithMessage("§c[VillagerRoller] Target is invalid. Stopping.");
            return;
        }

        switch (currentState) {
            case IDLE -> {
            }

            case CHECK_VILLAGER -> {
                if (targetVillager.getVillagerData().getProfession() == VillagerProfession.NONE) {
                    setState(State.PLACE_BLOCK);
                } else {
                    setState(State.OPEN_GUI);
                }
            }

            case PLACE_BLOCK -> {
                if (mc.level.getBlockState(jobBlockPos).getBlock() != jobBlock) {
                    if (placeBlock(jobBlockPos)) {
                        setState(State.WAIT_FOR_JOB);
                    }
                } else {
                    setState(State.WAIT_FOR_JOB);
                }
            }

            case WAIT_FOR_JOB -> {
                if (targetVillager.getVillagerData().getProfession() != VillagerProfession.NONE) {
                    setState(State.OPEN_GUI);
                }
            }

            case OPEN_GUI -> {
                if (mc.screen instanceof MerchantScreen) {
                    setState(State.CHECK_TRADES);
                    return;
                }

                if (tickCounter == 0 || tickCounter % 20 == 0) {
                    if (mc.gameMode != null) {
                        mc.gameMode.interact(mc.player, targetVillager, InteractionHand.MAIN_HAND);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
                tickCounter++;
            }

            case CHECK_TRADES -> {
                if (!(mc.screen instanceof MerchantScreen screen)) {
                    setState(State.OPEN_GUI);
                    return;
                }

                MerchantOffers offers = screen.getMenu().getOffers();
                if (offers.isEmpty()) {
                    return;
                }

                if (checkOffers(offers)) {
                    stopWithMessage("§a[VillagerRoller] Target Trade Found! Stopping.");
                    return;
                }

                mc.player.closeContainer();
                mc.setScreen(null);
                setState(State.BREAK_BLOCK);
            }

            case BREAK_BLOCK -> {
                BlockState state = mc.level.getBlockState(jobBlockPos);

                if (state.getBlock() != jobBlock) {
                    if (mc.gameMode != null) {
                        mc.gameMode.stopDestroyBlock();
                    }
                    setState(State.WAIT_FOR_UNEMPLOYED);
                    return;
                }

                int bestSlot = findBestTool(state);
                if (bestSlot != -1 && mc.player.getInventory().selected != bestSlot) {
                    mc.player.getInventory().selected = bestSlot;
                }

                if (mc.gameMode != null) {
                    mc.gameMode.continueDestroyBlock(jobBlockPos, Direction.UP);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }

            case WAIT_FOR_UNEMPLOYED -> {
                if (targetVillager.getVillagerData().getProfession() == VillagerProfession.NONE) {
                    setState(State.PLACE_BLOCK);
                }
            }
        }
    }

    private void setState(State newState) {
        currentState = newState;
        tickCounter = 0;
    }

    private int findBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean checkOffers(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();

            if (wantedItems.contains(result.getItem())) {
                sendMessage("§a[VillagerRoller] Found Wanted Item: " + result.getHoverName().getString());
                return true;
            }

            if (result.getItem() != Items.ENCHANTED_BOOK) {
                continue;
            }

            var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);

            for (var entry : enchantments.entrySet()) {
                Holder<Enchantment> enchantmentHolder = entry.getKey();
                String key = enchantmentHolder.unwrapKey().map(k -> k.location().toString()).orElse(null);
                if (key == null) {
                    continue;
                }

                int level = entry.getIntValue();
                int price = offer.getCostA().getCount();
                String enchantName = Enchantment.getFullname(enchantmentHolder, level).getString();

                if (!wantedEnchantments.contains(key)) {
                    sendMessage(String.format(
                            "§7[VillagerRoller] Seen: %s | Price: %d",
                            enchantName,
                            price
                    ));
                    continue;
                }

                EnchantmentData data = wantedEnchantments.getData(key);
                if (level >= data.minLevel && price <= data.maxPrice) {
                    sendMessage(String.format(
                            "§a[VillagerRoller] Found: %s | Price: %d",
                            enchantName,
                            price
                    ));
                    return true;
                }
                sendMessage(String.format(
                        "§7[VillagerRoller] Seen: %s | Price: %d | Wanted: Lv%d+, <=%d",
                        enchantName,
                        price,
                        data.minLevel,
                        data.maxPrice
                ));
            }
        }

        return false;
    }

    private boolean placeBlock(BlockPos pos) {
        PlacementSource source = findPlacementSource(jobBlock);
        if (source == null) {
            stopWithMessage("§c[VillagerRoller] Job block missing from hotbar/offhand!");
            return false;
        }

        int previousSlot = mc.player.getInventory().selected;

        if (source.isHotbarSwap()) {
            mc.player.getInventory().selected = source.hotbarSlot();
        }

        if (mc.gameMode != null) {
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, source.hand(), hitResult);
        }

        if (source.isHotbarSwap()) {
            mc.player.getInventory().selected = previousSlot;
        }

        return true;
    }

    private PlacementSource findPlacementSource(Block block) {
        ItemStack offhand = mc.player.getOffhandItem();
        if (!offhand.isEmpty() && Block.byItem(offhand.getItem()) == block) {
            return new PlacementSource(InteractionHand.OFF_HAND, -1);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && Block.byItem(stack.getItem()) == block) {
                return new PlacementSource(InteractionHand.MAIN_HAND, i);
            }
        }

        return null;
    }

    public enum State {
        IDLE,
        CHECK_VILLAGER,
        PLACE_BLOCK,
        WAIT_FOR_JOB,
        OPEN_GUI,
        CHECK_TRADES,
        BREAK_BLOCK,
        WAIT_FOR_UNEMPLOYED
    }
    private record PlacementSource(InteractionHand hand, int hotbarSlot) {
        boolean isHotbarSwap() {
            return hotbarSlot >= 0;
        }
    }
}