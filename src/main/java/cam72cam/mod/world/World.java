package cam72cam.mod.world;

import alexiil.mc.lib.attributes.fluid.FixedFluidInv;
import alexiil.mc.lib.attributes.fluid.FluidAttributes;
import alexiil.mc.lib.attributes.item.FixedItemInv;
import alexiil.mc.lib.attributes.item.ItemAttributes;
import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.block.BlockType;
import cam72cam.mod.block.tile.TileEntity;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Living;
import cam72cam.mod.entity.ModdedEntity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.boundingbox.BoundingBox;
import cam72cam.mod.entity.boundingbox.IBoundingBox;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.event.mixin.WorldChunkMixin;
import cam72cam.mod.fluid.ITank;
import cam72cam.mod.item.IInventory;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.util.TagCompound;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class World {

    /* Static access to loaded worlds */
    private static Map<net.minecraft.world.World, World> clientWorlds = new HashMap<>();
    private static Map<net.minecraft.world.World, World> serverWorlds = new HashMap<>();
    private static Map<Integer, World> clientWorldsByID = new HashMap<>();
    private static Map<Integer, World> serverWorldsByID = new HashMap<>();
    private static List<Consumer<World>> onTicks = new ArrayList<>();

    public final net.minecraft.world.World internal;
    public final boolean isClient;
    public final boolean isServer;
    private final List<Entity> entities;
    private final Map<Integer, Entity> entityByID;
    private final Map<UUID, Entity> entityByUUID;
    private long ticks;

    /* World Initialization */

    private World(net.minecraft.world.World world) {
        internal = world;
        isClient = world.isClient;
        isServer = !world.isClient;
        entities = new ArrayList<>();
        entityByID = new HashMap<>();
        entityByUUID = new HashMap<>();
    }

    public static void registerEvents() {
        WorldChunkMixin.JOIN_EVENT.register(entity -> get(entity.world).onEntityAdded(entity));
        WorldChunkMixin.LEAVE_EVENT.register(entity -> get(entity.world).onEntityRemoved(entity));

        CommonEvents.World.LOAD.subscribe(world -> {
            Map<net.minecraft.world.World, World> worlds = world.isClient ? clientWorlds : serverWorlds;
            Map<Integer, World> worldsByID = world.isClient ? clientWorldsByID : serverWorldsByID;

            World worldWrap = new World(world);
            worlds.put(world, worldWrap);
            worldsByID.put(worldWrap.getId(), worldWrap);
        });

        CommonEvents.World.UNLOAD.subscribe(world -> {
            Map<net.minecraft.world.World, World> worlds = world.isClient ? clientWorlds : serverWorlds;
            Map<Integer, World> worldsByID = world.isClient ? clientWorldsByID : serverWorldsByID;

            worlds.remove(world);
            worldsByID.remove(world.getDimension().getType().getRawId());
        });

        CommonEvents.World.TICK.subscribe(world -> {
            onTicks.forEach(fn -> fn.accept(get(world)));
            get(world).ticks++;
        });
    }

    public static World get(net.minecraft.world.World world) {
        if (world == null) {
            return null;
        }
        Map<net.minecraft.world.World, World> worlds = world.isClient ? clientWorlds : serverWorlds;

        return worlds.get(world);
    }

    public static World get(int dimID, boolean isClient) {
        Map<Integer, World> worldsByID = isClient ? clientWorldsByID : serverWorldsByID;

        return worldsByID.get(dimID);
    }

    public static void onTick(Consumer<World> fn) {
        onTicks.add(fn);
    }

    public int getId() {
        return internal.getDimension().getType().getRawId();
    }

    public boolean doesBlockCollideWith(Vec3i bp, IBoundingBox bb) {
        IBoundingBox bbb = IBoundingBox.from(internal.getBlockState(bp.internal).getCollisionShape(internal, bp.internal).getBoundingBox());
        return bbb != null && bb.intersects(bbb);
    }

    /* Event Methods */

    void onEntityAdded(net.minecraft.entity.Entity entityIn) {
        Entity entity;
        if (entityIn instanceof ModdedEntity) {
            entity = ((ModdedEntity) entityIn).getSelf();
        } else if (entityIn instanceof PlayerEntity) {
            entity = new Player((PlayerEntity) entityIn);
        } else if (entityIn instanceof MobEntity) {
            entity = new Living((MobEntity) entityIn);
        } else {
            entity = new Entity(entityIn);
        }
        entities.add(entity);
        entityByID.put(entityIn.getEntityId(), entity);
        entityByUUID.put(entity.getUUID(), entity);
    }

    void onEntityRemoved(net.minecraft.entity.Entity entity) {
        if (entityByUUID.containsKey(entity.getUuid())) {
            entities.remove(entityByUUID.get(entity.getUuid()));
            entityByID.remove(entity.getEntityId());
            entityByUUID.remove(entity.getUuid());
        }
    }

    /* Entity Methods */

    public Entity getEntity(net.minecraft.entity.Entity entity) {
        return getEntity(entity.getUuid(), Entity.class);
    }

    public <T extends Entity> T getEntity(int id, Class<T> type) {
        Entity ent = entityByID.get(id);
        if (ent == null) {
            return null;
        }
        if (!type.isInstance(ent)) {
            ModCore.warn("When looking for entity %s by id %s, we instead got a %s", type, id, ent.getClass());
            return null;
        }
        return (T) ent;
    }

    public <T extends Entity> T getEntity(UUID id, Class<T> type) {
        Entity ent = entityByUUID.get(id);
        if (ent == null) {
            return null;
        }
        if (!type.isInstance(ent)) {
            ModCore.warn("When looking for entity %s by id %s, we instead got a %s", type, id, ent.getClass());
            return null;
        }
        return (T) ent;
    }

    public <T extends Entity> List<T> getEntities(Class<T> type) {
        return getEntities((T val) -> true, type);
    }

    public <T extends Entity> List<T> getEntities(Predicate<T> filter, Class<T> type) {
        return entities.stream().map(entity -> entity.as(type)).filter(Objects::nonNull).filter(filter).collect(Collectors.toList());
    }

    public boolean spawnEntity(Entity ent) {
        return internal.spawnEntity(ent.internal);
    }


    public void keepLoaded(Vec3i pos) {
        //ChunkManager.flagEntityPos(this, pos);
    }


    public <T extends BlockEntity> List<T> getBlockEntities(Class<T> cls) {
        return internal.blockEntities.stream()
                .filter(x -> x instanceof cam72cam.mod.block.tile.TileEntity && ((TileEntity) x).isLoaded() && cls.isInstance(((TileEntity) x).instance()))
                .map(x -> (T) ((TileEntity) x).instance())
                .collect(Collectors.toList());
    }

    public <T extends net.minecraft.block.entity.BlockEntity> T getTileEntity(Vec3i pos, Class<T> cls) {
        return getTileEntity(pos, cls, true);
    }

    public <T extends net.minecraft.block.entity.BlockEntity> T getTileEntity(Vec3i pos, Class<T> cls, boolean create) {
        net.minecraft.block.entity.BlockEntity ent = internal.getWorldChunk(pos.internal).getBlockEntity(pos.internal, create ? WorldChunk.CreationType .IMMEDIATE : WorldChunk.CreationType.CHECK);
        if (cls.isInstance(ent)) {
            return (T) ent;
        }
        return null;
    }

    public <T extends BlockEntity> T getBlockEntity(Vec3i pos, Class<T> cls) {
        TileEntity te = getTileEntity(pos, TileEntity.class);
        if (te == null) {
            return null;
        }
        BlockEntity instance = te.instance();
        if (cls.isInstance(instance)) {
            return (T) instance;
        }
        return null;
    }

    public <T extends BlockEntity> boolean hasBlockEntity(Vec3i pos, Class<T> cls) {
        TileEntity te = getTileEntity(pos, TileEntity.class);
        if (te == null) {
            return false;
        }
        return cls.isInstance(te.instance());
    }

    public BlockEntity reconstituteBlockEntity(TagCompound data) {
        TileEntity te = (TileEntity) net.minecraft.block.entity.BlockEntity.createFromTag(data.internal);
        if (te == null) {
            System.out.println("BAD TE DATA " + data);
            return null;
        }
        if (te.instance() == null) {
            System.out.println("Loaded " + te.isLoaded() + " " + data);
        }
        return te.instance();
    }

    public void setBlockEntity(Vec3i pos, BlockEntity entity) {
        internal.setBlockEntity(pos.internal, entity.internal);
        entity.markDirty();
    }

    public void setToAir(Vec3i pos) {
        internal.clearBlockState(pos.internal, false);
    }

    public long getTime() {
        return internal.getTime();
    }

    public long getTicks() {
        return ticks;
    }

    public double getTPS(int sampleSize) {
        if (internal.getServer() == null) {
            return 20;
        }

        long[] ttl = internal.getServer().lastTickLengths;

        sampleSize = Math.min(sampleSize, ttl.length);
        double ttus = 0;
        for (int i = 0; i < sampleSize; i++) {
            ttus += ttl[ttl.length - 1 - i] / (double) sampleSize;
        }

        if (ttus == 0) {
            ttus = 0.01;
        }

        double ttms = ttus * 1.0E-6D;
        return Math.min(1000.0 / ttms, 20);
    }

    public Vec3i getPrecipitationHeight(Vec3i offset) {
        return new Vec3i(internal.getTopPosition(Heightmap.Type.MOTION_BLOCKING, offset.internal));
    }

    public boolean isAir(Vec3i ph) {
        return internal.isAir(ph.internal);
    }

    public void setSnowLevel(Vec3i ph, int snowDown) {
        internal.setBlockState(ph.internal, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, snowDown));
    }

    public int getSnowLevel(Vec3i ph) {
        BlockState state = internal.getBlockState(ph.internal);
        if (state.getBlock() == Blocks.SNOW) {
            return state.get(SnowBlock.LAYERS);
        }
        return 0;
    }

    public boolean isSnow(Vec3i ph) {
        net.minecraft.block.Block block = internal.getBlockState(ph.internal).getBlock();
        return block == Blocks.SNOW_BLOCK || block == Blocks.SNOW;
    }

    public boolean isSnowBlock(Vec3i ph) {
        return internal.getBlockState(ph.internal).getBlock() == Blocks.SNOW_BLOCK;
    }

    public boolean isPrecipitating() {
        return internal.isRaining();
    }

    public boolean isBlockLoaded(Vec3i parent) {
        return internal.isBlockLoaded(parent.internal);
    }

    public void breakBlock(Vec3i pos) {
        this.breakBlock(pos, true);
    }

    public void breakBlock(Vec3i pos, boolean drop) {
        internal.breakBlock(pos.internal, drop);
    }

    public void dropItem(ItemStack stack, Vec3i pos) {
        dropItem(stack, new Vec3d(pos));
    }

    public void dropItem(ItemStack stack, Vec3d pos) {
        internal.spawnEntity(new ItemEntity(internal, pos.x, pos.y, pos.z, stack.internal));
    }

    public void setBlock(Vec3i pos, BlockType block) {
        internal.setBlockState(pos.internal, block.internal.getDefaultState());
    }

    public void setBlock(Vec3i pos, ItemStack stack) {
        BlockState state = Block.getBlockFromItem(stack.internal.getItem()).getDefaultState(); //TODO .getPlacementState();
        internal.setBlockState(pos.internal, state);
    }

    public boolean isTopSolid(Vec3i pos) {
        return internal.getBlockState(pos.internal).isSideSolidFullSquare(internal, pos.internal, Direction.UP);
    }

    public int getRedstone(Vec3i pos) {
        return internal.getReceivedRedstonePower(pos.internal);
    }

    public void removeEntity(cam72cam.mod.entity.Entity entity) {
        entity.internal.remove();
    }

    public boolean canSeeSky(Vec3i position) {
        return internal.isSkyVisible(position.internal);
    }

    public boolean isRaining(Vec3i position) {
        return internal.hasRain(position.internal);
    }

    public boolean isSnowing(Vec3i position) {
        if (!internal.isRaining()) {
            return false;
        } else if (!internal.isSkyVisible(position.internal)) {
            return false;
        } else if (internal.getTopPosition(Heightmap.Type.MOTION_BLOCKING, position.internal).getY() > position.internal.getY()) {
            return false;
        } else {
            return internal.getBiome(position.internal).getPrecipitation() == Biome.Precipitation.RAIN;
        }
    }

    public float getTemperature(Vec3i pos) {
        float mctemp = internal.getBiome(pos.internal).getTemperature(pos.internal);
        //https://www.reddit.com/r/Minecraft/comments/3eh7yu/the_rl_temperature_of_minecraft_biomes_revealed/ctex050/
        return (13.6484805403f * mctemp) + 7.0879687222f;
    }

    public boolean isBlock(Vec3i pos, BlockType block) {
        return internal.getBlockState(pos.internal).getBlock() == block.internal;
    }

    public boolean isReplacable(Vec3i pos) {
        if (isAir(pos)) {
            return true;
        }

        return internal.getBlockState(pos.internal).getMaterial().isReplaceable();
    }

    /* Capabilities */
    public IInventory getInventory(Vec3i offset) {
        FixedItemInv inv = ItemAttributes.FIXED_INV.getFirstOrNull(internal, offset.internal);
        return IInventory.from(inv);
    }

    public ITank getTank(Vec3i offset) {
        FixedFluidInv inv = FluidAttributes.FIXED_INV.getFirstOrNull(internal, offset.internal);
        return ITank.getTank(inv);
    }

    public ItemStack getItemStack(Vec3i pos) {
        BlockState state = internal.getBlockState(pos.internal);
        return new ItemStack(state.getBlock().getPickStack(internal, pos.internal, state));
    }

    public List<ItemStack> getDroppedItems(IBoundingBox bb) {
        List<ItemEntity> items = internal.getEntities(ItemEntity.class, new BoundingBox(bb));
        return items.stream().map((ItemEntity::getStack)).map(ItemStack::new).collect(Collectors.toList());
    }

    public BlockInfo getBlock(Vec3i pos) {
        return new BlockInfo(internal.getBlockState(pos.internal));
    }

    public void setBlock(Vec3i pos, BlockInfo info) {
        internal.setBlockState(pos.internal, info.internal);
    }

    public boolean canEntityCollideWith(Vec3i bp, String damageType) {
        Block block = internal.getBlockState(bp.internal).getBlock();
        return block instanceof IConditionalCollision &&
                ((IConditionalCollision) block).canCollide(internal, bp.internal, internal.getBlockState(bp.internal), new EntityDamageSource(damageType, null));
    }

    public void createParticle(ParticleType type, Vec3d position, Vec3d velocity) {
        internal.addParticle(type.internal, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }

    public enum ParticleType {
        SMOKE(ParticleTypes.SMOKE),
        ;

        private final ParticleEffect internal;

        ParticleType(ParticleEffect internal) {
            this.internal = internal;
        }
    }
}
