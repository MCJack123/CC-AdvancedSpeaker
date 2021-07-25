package cc.craftospc.AdvancedSpeaker;

import com.google.common.collect.Sets;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

@Mod(AdvancedSpeaker.MOD_ID)
public final class AdvancedSpeaker {
    public static final String MOD_ID = "ccadvancedspeaker";
    public static final Logger LOGGER = LogManager.getLogger("CC Advanced Speaker");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITIES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, MOD_ID);

    public static final RegistryObject<Block> ADVANCED_SPEAKER_BLOCK = BLOCKS.register("advanced_speaker", () -> new AdvancedSpeakerBlock(AbstractBlock.Properties.of(Material.STONE).strength(2)));
    public static final RegistryObject<Item> ADVANCED_SPEAKER_ITEM = ITEMS.register("advanced_speaker", () -> new AdvancedSpeakerItem(ADVANCED_SPEAKER_BLOCK.get(), new Item.Properties().tab(ItemGroup.TAB_REDSTONE)));
    public static final RegistryObject<TileEntityType<?>> ADVANCED_SPEAKER_TE = TILE_ENTITIES.register("advanced_speaker", () -> new TileEntityType<>(AdvancedSpeakerTileEntity::new, Sets.newHashSet(ADVANCED_SPEAKER_BLOCK.get()), null));

    public static class PeripheralProvider implements IPeripheralProvider {
        @Nonnull
        @Override
        public LazyOptional<IPeripheral> getPeripheral(@Nonnull World world, @Nonnull BlockPos blockPos, @Nonnull Direction direction) {
            TileEntity block = world.getBlockEntity(blockPos);
            if (block instanceof AdvancedSpeakerTileEntity) {
                return LazyOptional.of(() -> ((AdvancedSpeakerTileEntity)block).peripheral);
            }
            return LazyOptional.empty();
        }
    }

    public AdvancedSpeaker() {
        LOGGER.info("Loaded Advanced Speaker");

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        TILE_ENTITIES.register(bus);
        ComputerCraftAPI.registerPeripheralProvider(new PeripheralProvider());
    }
}