package cam72cam.mod.entity.custom;

import cam72cam.mod.serialization.TagCompound;

public interface IWorldData {
    IWorldData NOP = new IWorldData() {
        @Override
        public void load(TagCompound data) {

        }

        @Override
        public void save(TagCompound data) {

        }
    };

    static IWorldData get(Object o) {
        if (o instanceof IWorldData) {
            return (IWorldData) o;
        }
        return IWorldData.NOP;
    }

    /** World Load */
    void load(TagCompound data);

    /** World Save */
    void save(TagCompound data);
}
