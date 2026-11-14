/**
 * Chroma Hub editor logic, pure Java: the client-side working copy of one hub ({@link EditorModel}) with its 32-step
 * undo, op-batch building, the orbit camera, ray-vs-voxel picking, flood and gradient fills, the viewport rasteriser
 * with its level-of-detail switch, and the wire codecs for the hub screen's own packets ({@link HubDetail},
 * {@link HubAction}).
 *
 * <p>Imports {@code java.*} and the pure {@code chroma} core only, so everything here is unit-tested without a game
 * bootstrap and behaves identically on every Minecraft version and loader.
 */
package za.co.neroland.nerodecor.chroma.editor;
