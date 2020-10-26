package xyz.nucleoid.plasmid.game.map.template;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.IdListPalette;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Represents a map template
 */
public final class MapTemplate {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    final Long2ObjectMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    final Long2ObjectMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
    final List<TemplateRegion> regions = new ArrayList<>();

    RegistryKey<Biome> biome = BiomeKeys.THE_VOID;

    BlockBounds bounds = null;

    private MapTemplate() {
    }

    public static MapTemplate createEmpty() {
        return new MapTemplate();
    }

    /**
     * Sets the biome key of the map template.
     *
     * @param biome The biome key.
     */
    public void setBiome(RegistryKey<Biome> biome) {
        this.biome = biome;
    }

    /**
     * Returns the biome key of the map template.
     *
     * @return The biome key.
     */
    public RegistryKey<Biome> getBiome() {
        return this.biome;
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        Chunk chunk = this.chunks.computeIfAbsent(chunkPos(pos), p -> new Chunk());
        chunk.set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);

        if (state.getBlock().hasBlockEntity()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "DUMMY");
            this.blockEntities.put(pos.asLong(), tag);
        }
    }

    public void setBlockEntity(BlockPos pos, @Nullable BlockEntity entity) {
        if (entity != null) {
            CompoundTag entityTag = entity.toTag(new CompoundTag());
            entityTag.putInt("x", pos.getX());
            entityTag.putInt("y", pos.getY());
            entityTag.putInt("z", pos.getZ());

            this.blockEntities.put(pos.asLong(), entityTag);
        } else {
            this.blockEntities.remove(pos.asLong());
        }
    }

    public TemplateRegion addRegion(String marker, BlockBounds bounds) {
        return this.addRegion(marker, bounds, new CompoundTag());
    }

    public TemplateRegion addRegion(String marker, BlockBounds bounds, CompoundTag tag) {
        TemplateRegion region = new TemplateRegion(marker, bounds, tag);
        this.regions.add(region);
        return region;
    }

    public void addRegion(TemplateRegion region) {
        this.regions.add(region);
    }

    public BlockState getBlockState(BlockPos pos) {
        Chunk chunk = this.chunks.get(chunkPos(pos));
        if (chunk != null) {
            return chunk.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
        return AIR;
    }

    @Nullable
    public CompoundTag getBlockEntityTag(BlockPos pos) {
        return this.blockEntities.get(pos.asLong());
    }

    /**
     * Adds an entity to the map template.
     * <p>
     * The position of the entity must be relative to the map template.
     *
     * @param entity The entity to add.
     */
    void addEntity(Entity entity) {
        this.chunks.computeIfAbsent(chunkPos(entity.getBlockPos()), p -> new Chunk()).addEntity(entity);
    }

    /**
     * Returns a stream of serialized entities from the chunk containing the specified block position.
     *
     * @param pos A block position.
     * @return The stream of entities.
     */
    public Stream<CompoundTag> getEntitiesInChunk(BlockPos pos) {
        return this.getEntitiesInChunk(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    /**
     * Returns a stream of serialized entities from a chunk.
     *
     * @param chunkX The chunk X-coordinate.
     * @param chunkY The chunk Y-coordinate.
     * @param chunkZ The chunk Z-coordinate.
     * @return The stream of entities.
     */
    public Stream<CompoundTag> getEntitiesInChunk(int chunkX, int chunkY, int chunkZ) {
        return this.chunks.get(ChunkSectionPos.asLong(chunkX, chunkY, chunkZ)).getEntities().stream();
    }

    // TODO: store / lookup more efficiently?
    public int getTopY(int x, int z, Heightmap.Type heightmap) {
        Predicate<BlockState> predicate = heightmap.getBlockPredicate();

        BlockPos.Mutable mutablePos = new BlockPos.Mutable(x, 0, z);
        for (int y = 255; y >= 0; y--) {
            mutablePos.setY(y);

            BlockState state = this.getBlockState(mutablePos);
            if (predicate.test(state)) {
                return y;
            }
        }

        return 0;
    }

    public BlockPos getTopPos(int x, int z, Heightmap.Type heightmap) {
        int y = this.getTopY(x, z, heightmap);
        return new BlockPos(x, y, z);
    }

    public Stream<TemplateRegion> getTemplateRegions(String marker) {
        return this.regions.stream()
                .filter(region -> region.getMarker().equals(marker));
    }

    public Stream<BlockBounds> getRegions(String marker) {
        return this.getTemplateRegions(marker)
                .map(TemplateRegion::getBounds);
    }

    @Nullable
    public TemplateRegion getFirstTemplateRegion(String marker) {
        return this.getTemplateRegions(marker).findFirst().orElse(null);
    }

    @Nullable
    public BlockBounds getFirstRegion(String marker) {
        return this.getRegions(marker).findFirst().orElse(null);
    }

    public boolean containsBlock(BlockPos pos) {
        return this.getBlockState(pos) != AIR;
    }

    private static long chunkPos(BlockPos pos) {
        return ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public void setBounds(BlockBounds bounds) {
        this.bounds = bounds;
    }

    public BlockBounds getBounds() {
        if (this.bounds == null) {
            this.bounds = this.computeBounds();
        }
        return this.bounds;
    }

    private BlockBounds computeBounds() {
        int minChunkX = Integer.MAX_VALUE;
        int minChunkY = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkY = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (Long2ObjectMap.Entry<Chunk> entry : Long2ObjectMaps.fastIterable(this.chunks)) {
            long chunkPos = entry.getLongKey();
            int chunkX = ChunkSectionPos.unpackX(chunkPos);
            int chunkY = ChunkSectionPos.unpackY(chunkPos);
            int chunkZ = ChunkSectionPos.unpackZ(chunkPos);

            if (chunkX < minChunkX) minChunkX = chunkX;
            if (chunkY < minChunkY) minChunkY = chunkY;
            if (chunkZ < minChunkZ) minChunkZ = chunkZ;

            if (chunkX > maxChunkX) maxChunkX = chunkX;
            if (chunkY > maxChunkY) maxChunkY = chunkY;
            if (chunkZ > maxChunkZ) maxChunkZ = chunkZ;
        }

        return new BlockBounds(
                new BlockPos(minChunkX << 4, minChunkY << 4, minChunkZ << 4),
                new BlockPos((maxChunkX << 4) + 15, (maxChunkY << 4) + 15, (maxChunkZ << 4) + 15)
        );
    }

    /**
     * Represents a cubic chunk.
     */
    static class Chunk {
        private static final Palette<BlockState> PALETTE = new IdListPalette<>(Block.STATE_IDS, Blocks.AIR.getDefaultState());

        private final PalettedContainer<BlockState> container = new PalettedContainer<>(
                PALETTE, Block.STATE_IDS,
                NbtHelper::toBlockState, NbtHelper::fromBlockState,
                Blocks.AIR.getDefaultState()
        );
        private final List<CompoundTag> entities = new ArrayList<>();

        public void set(int x, int y, int z, BlockState state) {
            this.container.set(x, y, z, state);
        }

        public BlockState get(int x, int y, int z) {
            return this.container.get(x, y, z);
        }

        /**
         * Adds an entity to this chunk.
         * <p>
         * The position of the entity must be relative to the map template.
         *
         * @param entity The entity to add.
         */
        public void addEntity(Entity entity) {
            CompoundTag tag = new CompoundTag();

            if (!entity.saveToTag(tag)) { return; }

            // Avoid conflicts.
            tag.remove("UUID");

            ListTag posTag = new ListTag();
            BlockPos pos = entity.getBlockPos();

            int offsetX = pos.getX() & 15;
            int offsetY = pos.getY() & 15;
            int offsetZ = pos.getZ() & 15;

            posTag.add(DoubleTag.of(entity.getX() - (double) (pos.getX() - offsetX)));
            posTag.add(DoubleTag.of(entity.getY() - (double) (pos.getY() - offsetY)));
            posTag.add(DoubleTag.of(entity.getZ() - (double) (pos.getZ() - offsetZ)));

            tag.put("Pos", posTag);

            // AbstractDecorationEntity has special position handling with an attachment position.
            if (entity instanceof AbstractDecorationEntity) {
                AbstractDecorationEntity decorationEntity = (AbstractDecorationEntity) entity;
                pos = decorationEntity.getDecorationBlockPos();

                int blockPosX = pos.getX() & 15;
                if (offsetX == 0 && blockPosX == 15) { blockPosX = -1; }
                int blockPosY = pos.getY() & 15;
                if (offsetY == 0 && blockPosY == 15) { blockPosY = -1; }
                int blockPosZ = pos.getZ() & 15;
                if (offsetZ == 0 && blockPosZ == 15) { blockPosZ = -1; }

                tag.putInt("TileX", blockPosX);
                tag.putInt("TileY", blockPosY);
                tag.putInt("TileZ", blockPosZ);
            }

            this.entities.add(tag);
        }

        /**
         * Returns the entities of this chunk.
         *
         * @return The entities of this chunk.
         */
        public List<CompoundTag> getEntities() {
            return this.entities;
        }

        public void serialize(CompoundTag tag) {
            this.container.write(tag, "palette", "block_states");
            ListTag entitiesTag = new ListTag();
            entitiesTag.addAll(this.entities);
            tag.put("entities", entitiesTag);
        }

        public static Chunk deserialize(CompoundTag tag) {
            Chunk chunk = new Chunk();
            chunk.container.read(tag.getList("palette", NbtType.COMPOUND), tag.getLongArray("block_states"));
            ListTag entitiesTag = tag.getList("entities", NbtType.COMPOUND);
            entitiesTag.forEach(entityTag -> chunk.entities.add((CompoundTag) entityTag));
            return chunk;
        }
    }
}
