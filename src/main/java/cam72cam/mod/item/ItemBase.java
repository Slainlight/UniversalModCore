package cam72cam.mod.item;

import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ItemBase {
    public final Item internal;
    public ItemBase(String modID, String name) {
        internal = new ItemInternal();
        internal.setUnlocalizedName(modID + ":" + name);
        internal.setRegistryName(new ResourceLocation(modID, name));
        internal.setMaxStackSize(getStackSize());
        internal.setCreativeTab(getCreativeTabs().get(0).internal);

        CommonEvents.Item.REGISTER.subscribe(() -> ForgeRegistries.ITEMS.register(internal));
    }

    public abstract List<CreativeTab> getCreativeTabs();

    public int getStackSize() {
        return 64;
    }

    public List<ItemStack> getItemVariants(CreativeTab creativeTab) {
        List<ItemStack> res = new ArrayList<>();
        if (creativeTab == null || creativeTab.internal == internal.getCreativeTab()) {
            res.add(new ItemStack(internal, 1));
        }
        return res;
    }

    public List<String> getTooltip(ItemStack itemStack) {
        return Collections.emptyList();
    }

    public ClickResult onClickBlock(Player player, World world, Vec3i vec3i, Hand from, Facing from1, Vec3d vec3d) {
        return ClickResult.PASS;
    }

    public void onClickAir(Player player, World world, Hand hand) {

    }

    public boolean isValidArmor(ItemStack itemStack, ArmorSlot from, Entity entity) {
        return false;
    }

    public String getCustomName(ItemStack stack) {
        return null;
    }

    /* Name Hacks */

    protected final void applyCustomName(ItemStack stack) {
        String custom = getCustomName(stack);
        if (custom != null) {
            stack.internal.setStackDisplayName(TextFormatting.RESET + custom);
        }
    }

    public Identifier getRegistryName() {
        return new Identifier(internal.getRegistryName());
    }

    @Optional.Interface(iface = "mezz.jei.api.ingredients.ISlowRenderItem", modid = "jei")
    private class ItemInternal extends Item {
        @Override
        public void getSubItems(Item itemIn, CreativeTabs tab, List<net.minecraft.item.ItemStack> items) {
            CreativeTab myTab = tab != CreativeTabs.SEARCH && tab != null ? new CreativeTab(tab) : null;
            items.addAll(getItemVariants(myTab).stream().map((ItemStack stack) -> stack.internal).collect(Collectors.toList()));
        }

        @Override
        @SideOnly(Side.CLIENT)
        public final void addInformation(net.minecraft.item.ItemStack stack, EntityPlayer entityPlayer, List<String> tooltip, boolean flagIn) {
            super.addInformation(stack, entityPlayer, tooltip, flagIn);
            applyCustomName(new ItemStack(stack));
            tooltip.addAll(ItemBase.this.getTooltip(new ItemStack(stack)));
        }

        @Override
        public final EnumActionResult onItemUse(net.minecraft.item.ItemStack stack, EntityPlayer player, net.minecraft.world.World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            return ItemBase.this.onClickBlock(new Player(player), World.get(worldIn), new Vec3i(pos), Hand.from(hand), Facing.from(facing), new Vec3d(hitX, hitY, hitZ)).internal;
        }

        @Override
        public final ActionResult<net.minecraft.item.ItemStack> onItemRightClick(net.minecraft.item.ItemStack stack, net.minecraft.world.World world, EntityPlayer player, EnumHand hand) {
            onClickAir(new Player(player), World.get(world), Hand.from(hand));
            return super.onItemRightClick(stack, world, player, hand);
        }

        @Override
        public final boolean isValidArmor(net.minecraft.item.ItemStack stack, EntityEquipmentSlot armorType, net.minecraft.entity.Entity entity) {
            return ItemBase.this.isValidArmor(new ItemStack(stack), ArmorSlot.from(armorType), new Entity(entity));
        }

        @Override
        public final String getUnlocalizedName(net.minecraft.item.ItemStack stack) {
            applyCustomName(new ItemStack(stack));
            return super.getUnlocalizedName(stack);
        }

        @Override
        public final CreativeTabs[] getCreativeTabs() {
            return ItemBase.this.getCreativeTabs().stream().map((CreativeTab tab) -> tab.internal).toArray(CreativeTabs[]::new);
        }
    }
}
