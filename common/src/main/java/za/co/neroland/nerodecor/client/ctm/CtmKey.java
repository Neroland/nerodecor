package za.co.neroland.nerodecor.client.ctm;

import net.minecraft.resources.Identifier;

/**
 * The connection identity of a CTM surface: two neighbouring faces connect iff their
 * keys are equal. A key folds together the block's <b>family</b> (which sprite set it
 * draws from) and its painted <b>colour</b> (the {@code DecorColor} ordinal, added in
 * Stage E), so a red neon strip never merges into a blue one and hull never merges into
 * glass.
 *
 * @param family the CTM sprite-set family (e.g. {@code nerodecor:hull})
 * @param colour the painted colour ordinal ({@code DecorColor.ordinal()}); use a single
 *               fixed value for surfaces that are not paintable
 */
public record CtmKey(Identifier family, int colour) {

    /** Whether this surface connects to {@code other} (same family and colour). */
    public boolean connectsTo(CtmKey other) {
        return other != null && colour == other.colour && family.equals(other.family);
    }
}
