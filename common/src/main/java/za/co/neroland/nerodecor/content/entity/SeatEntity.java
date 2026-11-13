package za.co.neroland.nerodecor.content.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
//? if >=1.21.2 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
//?}
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerodecor.registry.collections.LabGantryConduitsBlocks;

/**
 * {@code nerodecor:seat}: the invisible, model-less vehicle a player rides while sitting on a seat block (lab_stool,
 * operator_chair). Spawned by {@link #sit}, one per block. It is never written to chunk data, and it removes itself
 * the tick after its rider leaves, or as soon as the seat block is gone. A seat restored from a player's save (they
 * logged out seated) has no seat block and is removed on its first tick. Holds no player data.
 */
public class SeatEntity extends Entity {

    private static final int BLOCK_CHECK_INTERVAL = 10;

    /** The seat block's position and block; set server-side by {@link #sit}, never saved. */
    private BlockPos seatPos;
    private Block seatBlock;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Sits {@code player} on the seat block at {@code pos}, riding a seat entity whose position is
     * {@code pos.getY() + seatY} (x/z centred), so the rider's hips are at {@code seatY} above the block's base.
     * Call from the block's {@code useWithoutItem}; returns whether the use was consumed (client side: optimistic).
     * Refuses when the player is sneaking or already riding, or the seat is taken.
     */
    public static boolean sit(Level level, BlockPos pos, Player player, double seatY) {
        if (player.isPassenger() || player.isShiftKeyDown()) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        List<SeatEntity> existing = level.getEntitiesOfClass(SeatEntity.class,
                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1.5, pos.getZ() + 1));
        for (SeatEntity seat : existing) {
            if (seat.isVehicle()) {
                return false;
            }
            seat.discard();
        }
        SeatEntity seat = new SeatEntity(LabGantryConduitsBlocks.SEAT.get(), level);
        seat.seatPos = pos.immutable();
        seat.seatBlock = level.getBlockState(pos).getBlock();
        seat.setPos(pos.getX() + 0.5, pos.getY() + seatY, pos.getZ() + 0.5);
        seat.setYRot(player.getYRot());
        if (!level.addFreshEntity(seat)) {
            return false;
        }
        if (!player.startRiding(seat)) {
            seat.discard();
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (!isVehicle() || seatPos == null) {
            ejectPassengers();
            discard();
            return;
        }
        if (tickCount % BLOCK_CHECK_INTERVAL == 0 && !level().getBlockState(seatPos).is(seatBlock)) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos base = seatPos != null ? seatPos : blockPosition();
        Direction first = passenger.getDirection();
        Direction[] order = {first, first.getClockWise(), first.getCounterClockWise(), first.getOpposite()};
        for (Direction d : order) {
            Vec3 spot = standingSpot(passenger, base.relative(d));
            if (spot != null) {
                return spot;
            }
        }
        Vec3 above = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        return level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(above))
                ? above
                : super.getDismountLocationForPassenger(passenger);
    }

    /** Standing on the floor under {@code at}, if there is one and the passenger fits there. */
    private Vec3 standingSpot(LivingEntity passenger, BlockPos at) {
        BlockPos floor = at.below();
        net.minecraft.world.phys.shapes.VoxelShape floorShape = level().getBlockState(floor).getCollisionShape(level(), floor);
        if (floorShape.isEmpty()) {
            return null;
        }
        Vec3 spot = new Vec3(at.getX() + 0.5, floor.getY() + floorShape.max(Direction.Axis.Y), at.getZ() + 0.5);
        return level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(spot)) ? spot : null;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }
    //?}

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
    //?} else {
    /*@Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
    *///?}
}
