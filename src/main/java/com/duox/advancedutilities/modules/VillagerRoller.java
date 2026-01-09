package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting.EnchantmentData;
import com.duox.advancedutilities.system.settings.ItemListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

public class VillagerRoller extends Module {

    private final NumberSetting delay = new NumberSetting("Delay", 10.0, 5.0, 40.0, 1.0);
    private final ItemListSetting wantedItems = new ItemListSetting("Wanted Items");
    private final EnchantmentListSetting wantedEnchantments = new EnchantmentListSetting("Enchantments");

    private Villager targetVillager;
    private BlockPos jobBlockPos;
    private Block jobBlock;

    private State currentState = State.IDLE;
    private int tickCounter = 0;

    // For selection mode
    private boolean selectingVillager = false;
    private boolean selectingBlock = false;

    public VillagerRoller() {
        super("Villager Roller", "Auto-rolls villager trades.", Category.WORLD);
        addSetting(delay);
        addSetting(wantedItems);
        addSetting(wantedEnchantments);
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        if (targetVillager == null || jobBlockPos == null) {
            startSelection();
        } else {
            currentState = State.CHECK_VILLAGER;
        }
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        currentState = State.IDLE;
        selectingVillager = false;
        selectingBlock = false;
    }
    
    private void startSelection() {
        selectingVillager = true;
        selectingBlock = false;
        targetVillager = null;
        jobBlockPos = null;
        jobBlock = null;
        if (mc.player != null) {
             mc.player.displayClientMessage(Component.literal("§e[VillagerRoller] Step 1: Right-click the Villager."), true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!selectingVillager) return;
        if (event.getTarget() instanceof Villager villager) {
             targetVillager = villager;
             selectingVillager = false;
             selectingBlock = true;
             event.setCanceled(true); // Prevent opening GUI
             event.getEntity().displayClientMessage(Component.literal("§e[VillagerRoller] Step 2: Right-click the Job Block (or the spot for it)."), true);
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!selectingBlock) return;
        
        jobBlockPos = event.getPos();
        BlockState state = mc.level.getBlockState(jobBlockPos);
        jobBlock = state.getBlock();
        
        // If the block is air or bedrock, maybe user clicked through?
        // But for now, take the clicked block.
        // If user wants to replace it, they should ensure the correct block is there or I should check hand item.
        
        // Better: Check what item the user is holding. If it's a block item, assume that's the job block type.
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && Block.byItem(held.getItem()) != Blocks.AIR) {
             jobBlock = Block.byItem(held.getItem());
        }
        
        selectingBlock = false;
        currentState = State.CHECK_VILLAGER;
        event.setCanceled(true);
        event.getEntity().displayClientMessage(Component.literal("§a[VillagerRoller] Setup Complete! Starting roll..."), true);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        
        // Handle Selection Logic
        if (selectingVillager || selectingBlock) {
            handleSelection();
            return;
        }

        if (targetVillager == null || jobBlockPos == null || targetVillager.isRemoved()) {
            this.toggle(); // Disable if invalid
            return;
        }

        // We handle tick delays internally per state if needed, to maximize speed.
        
        switch (currentState) {
            case IDLE:
                break;
                
            case CHECK_VILLAGER:
                 // Check if villager has profession
                 VillagerProfession profession = targetVillager.getVillagerData().getProfession();
                 if (profession == VillagerProfession.NONE) {
                     // Villager is unemployed, we need to place the block
                     setState(State.PLACE_BLOCK);
                 } else {
                     // Villager has profession, let's check trades
                     // But first, we need to open the GUI
                     setState(State.OPEN_GUI);
                 }
                 break;

            case PLACE_BLOCK:
                // Check if block is already there
                if (mc.level.getBlockState(jobBlockPos).getBlock() != jobBlock) {
                    if (placeBlock(jobBlockPos)) {
                         setState(State.WAIT_FOR_JOB);
                    }
                } else {
                     setState(State.WAIT_FOR_JOB);
                }
                break;

            case WAIT_FOR_JOB:
                // Wait until villager picks up job
                if (targetVillager.getVillagerData().getProfession() != VillagerProfession.NONE) {
                    setState(State.OPEN_GUI);
                }
                // Optional: Timeout logic if needed, but for now we wait indefinitely or until user stops.
                break;

            case OPEN_GUI:
                // Interact to open GUI
                if (mc.screen instanceof MerchantScreen) {
                    setState(State.CHECK_TRADES);
                } else {
                    // Try to open GUI only if we are not looking at a screen
                    // Use delay to avoid spamming interaction packets
                    if (tickCounter == 0 || tickCounter % 20 == 0) {
                        if (mc.gameMode != null && targetVillager.distanceToSqr(mc.player) < 25) {
                            mc.gameMode.interact(mc.player, targetVillager, InteractionHand.MAIN_HAND);
                            mc.player.swing(InteractionHand.MAIN_HAND);// Reset counter after interaction attempt
                        }
                    }
                    tickCounter++;
                }
                break;

            case CHECK_TRADES:
                if (mc.screen instanceof MerchantScreen) {
                    MerchantScreen screen = (MerchantScreen) mc.screen;
                    MerchantOffers offers = screen.getMenu().getOffers();
                    
                    if (offers.isEmpty()) {
                        // Wait a bit for offers to sync?
                        return; 
                    }

                    if (checkOffers(offers)) {
                        // Found it!
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal("§aTarget Trade Found! Stopping."), false);
                        }
                        this.toggle(); // Disable module
                        return; // Ensure we stop processing this tick
                    } else {
                        // Not found
                        if (mc.player != null) {
                            mc.player.closeContainer();
                        }
                        mc.setScreen(null); // Force close client screen to allow mining immediately
                        setState(State.BREAK_BLOCK);
                    }
                } else {
                    // GUI closed unexpectedly?
                    setState(State.OPEN_GUI);
                }
                break;

            case BREAK_BLOCK:
                 // Break the job block
                 BlockState currentStateBlock = mc.level.getBlockState(jobBlockPos);
                 if (currentStateBlock.getBlock() == jobBlock) {
                      // Equip best tool
                      int bestSlot = findBestTool(currentStateBlock);
                      if (bestSlot != -1 && mc.player.getInventory().selected != bestSlot) {
                          mc.player.getInventory().selected = bestSlot;
                      }

                      if (mc.gameMode != null) {
                          // Legit mining - must be called every tick
                          mc.gameMode.continueDestroyBlock(jobBlockPos, Direction.UP);
                          mc.player.swing(InteractionHand.MAIN_HAND);
                      }
                 } else {
                     // Block is broken (or different)
                     if (mc.gameMode != null) {
                         mc.gameMode.stopDestroyBlock(); // Ensure we stop breaking
                     }
                     setState(State.WAIT_FOR_UNEMPLOYED);
                 }
                 break;

            case WAIT_FOR_UNEMPLOYED:
                if (targetVillager.getVillagerData().getProfession() == VillagerProfession.NONE) {
                    setState(State.PLACE_BLOCK);
                }
                break;
        }
    }
    
    private void setState(State newState) {
        this.currentState = newState;
        this.tickCounter = 0;
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

    private void handleSelection() {
        // Handled by events
    }
    
    // Check if offers contain desired items/enchantments
    private boolean checkOffers(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            
            // Check Item List
            if (wantedItems.contains(result.getItem())) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a[VillagerRoller] Found Wanted Item: " + result.getHoverName().getString()), false);
                }
                return true;
            }
            
            // Check Enchantments
            if (result.getItem() == Items.ENCHANTED_BOOK) {
                Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(result);
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    Enchantment ench = entry.getKey();
                    int level = entry.getValue();
                    int price = offer.getCostA().getCount(); // Main cost (Emeralds usually)
                    
                    if (mc.player != null) {
                         String logMsg = String.format("§7[Roller] Seen: %s %d | Price: %d", 
                                ench.getFullname(level).getString(), level, price);
                         mc.player.displayClientMessage(Component.literal(logMsg), false);
                    }

                    String enchId = BuiltInRegistries.ENCHANTMENT.getKey(ench).toString();
                    if (wantedEnchantments.contains(enchId)) {
                        EnchantmentData data = wantedEnchantments.getData(enchId);
                        
                        // Check constraints
                        if (level >= data.minLevel && price <= data.maxPrice) {
                            if (mc.player != null) {
                                String msg = String.format("§a[VillagerRoller] Found: %s %d | Price: %d", 
                                        ench.getFullname(level).getString(), level, price);
                                mc.player.displayClientMessage(Component.literal(msg), false);
                            }
                            return true;
                        } else {
                            // Log partial match?
                             if (mc.player != null) {
                                 String msg = String.format("§e[VillagerRoller] Skip: %s %d | Price: %d (Wanted: Lv%d+, Price<=%d)", 
                                        ench.getFullname(level).getString(), level, price, data.minLevel, data.maxPrice);
                                 mc.player.displayClientMessage(Component.literal(msg), true);
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    private boolean placeBlock(BlockPos pos) {
        // Find block in inventory
        int slot = findBlockInHotbar(jobBlock);
        if (slot == -1) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§cJob block missing from hotbar!"), false);
                this.toggle();
            }
            return false;
        }
        
        int prevSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = slot;
        
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        
        mc.player.getInventory().selected = prevSlot;
        return true;
    }

    private int findBlockInHotbar(Block block) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && Block.byItem(stack.getItem()) == block) {
                return i;
            }
        }
        return -1;
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
}
