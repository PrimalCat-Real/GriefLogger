package com.daqem.grieflogger.model;

import com.daqem.grieflogger.util.CompressionUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SimpleItemStack {

    private final Item item;
    private int count;
    private final DataComponentPatch tag;

    public SimpleItemStack(ItemStack itemStack) {
        this(itemStack.getItem(), itemStack.getCount(), itemStack.getComponentsPatch());
    }

    public SimpleItemStack(ResourceLocation itemLocation, int count, DataComponentPatch tag) {
        this.item = BuiltInRegistries.ITEM.get(itemLocation);
        this.count = count;
        this.tag = tag;
    }

    public SimpleItemStack(Item item, int count, DataComponentPatch tag) {
        this.item = item;
        this.count = count;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
        //DOES NOT CHECK COUNT
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleItemStack that = (SimpleItemStack) o;
        return Objects.equals(item, that.item) && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        // Must match equals() - does not include count
        return Objects.hash(item, tag);
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public DataComponentPatch getTag() {
        return tag;
    }

    public boolean hasTag() {
        return tag != null && !tag.isEmpty();
    }

    public boolean hasNoTag() {
        return tag == null || tag.isEmpty();
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void addCount(int count) {
        this.count += count;
    }

    /**
     * Serializes the tag to compressed bytes for database storage.
     * Uses GZIP compression to reduce storage size for large NBT data.
     *
     * @param level the level for registry access
     * @return compressed byte array, or null if no tag
     */
    public byte @Nullable [] getTagBytes(Level level) {
        if (tag == null) {
            return null;
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        DataComponentPatch.STREAM_CODEC.encode(buf, tag);
        byte[] rawBytes = new byte[buf.readableBytes()];
        buf.readBytes(rawBytes);
        return CompressionUtils.compress(rawBytes);
    }

    /**
     * Deserializes tag bytes from database storage.
     * Handles both compressed and legacy uncompressed data.
     *
     * @param data the byte array from database
     * @param level the level for registry access
     * @return the DataComponentPatch, or null if data is null/empty
     */
    public static @Nullable DataComponentPatch tagFromBytes(byte @Nullable [] data, Level level) {
        if (data == null || data.length == 0) {
            return null;
        }

        byte[] decompressed;
        if (CompressionUtils.hasCompressionHeader(data)) {
            decompressed = CompressionUtils.decompress(data);
        } else {
            // Legacy uncompressed data (for backwards compatibility)
            decompressed = data;
        }

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(decompressed), level.registryAccess());
        return DataComponentPatch.STREAM_CODEC.decode(buf);
    }

    public ItemStack toItemStack() {
        ItemStack itemStack = new ItemStack(item, count);
        itemStack.applyComponents(tag);
        return itemStack;
    }

    public boolean isEmpty() {
        return item.equals(ItemStack.EMPTY.getItem()) || count == 0;
    }
}
