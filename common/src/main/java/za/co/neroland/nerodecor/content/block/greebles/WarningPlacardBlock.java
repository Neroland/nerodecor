package za.co.neroland.nerodecor.content.block.greebles;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import za.co.neroland.nerodecor.content.block.lights.FaceMountedBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/** Warning placard: an enamel plate on {@code face}; empty-hand use cycles the {@code symbol} (sneak: back). */
public class WarningPlacardBlock extends FaceMountedBlock {

    public enum Symbol implements StringRepresentable {
        CAUTION, VOLTAGE, RADIATION, FLAMMABLE, CRYO, SUIT, NO_ENTRY, OXYGEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Symbol> SYMBOL = EnumProperty.create("symbol", Symbol.class);

    public WarningPlacardBlock(Properties properties) {
        super(properties, true, b(2, 2, 0, 14, 14, 1));
        registerDefaultState(defaultBlockState().setValue(SYMBOL, Symbol.CAUTION));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SYMBOL);
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        Symbol[] all = Symbol.values();
        int step = player.isShiftKeyDown() ? all.length - 1 : 1;
        return state.setValue(SYMBOL, all[(state.getValue(SYMBOL).ordinal() + step) % all.length]);
    }
}
