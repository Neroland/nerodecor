package za.co.neroland.nerodecor.command.showroom;

/** Extra steps a showroom bay runs after its blocks are placed. */
@FunctionalInterface
public interface ShowroomHook {

    void apply(Showroom.Bay bay);
}
