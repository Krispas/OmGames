package krispasi.omGames.hallsofcarnage;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HallsDisplayTransforms {
    private static final Vector3f BLOCK_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);

    private HallsDisplayTransforms() {
    }

    static Transformation centeredBlock(double scaleX, double scaleY, double scaleZ) {
        return centeredBlock(scaleX, scaleY, scaleZ, new Quaternionf());
    }

    static Transformation centeredBlock(double scaleX, double scaleY, double scaleZ, Quaternionf rotation) {
        Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
        Vector3f transformedCenter = new Vector3f(BLOCK_CENTER).mul(scale).rotate(rotation);
        return new Transformation(
                transformedCenter.negate(),
                new Quaternionf(rotation),
                scale,
                new Quaternionf());
    }

    static Transformation bottomCenteredBlock(double scaleX, double scaleY, double scaleZ) {
        return bottomCenteredBlock(scaleX, scaleY, scaleZ, new Quaternionf());
    }

    static Transformation bottomCenteredBlock(double scaleX, double scaleY, double scaleZ, Quaternionf rotation) {
        Vector3f scale = new Vector3f((float) scaleX, (float) scaleY, (float) scaleZ);
        Vector3f bottomCenter = new Vector3f(0.5f, 0.0f, 0.5f);
        Vector3f transformedBottomCenter = new Vector3f(bottomCenter).mul(scale).rotate(rotation);
        return new Transformation(
                transformedBottomCenter.negate(),
                new Quaternionf(rotation),
                scale,
                new Quaternionf());
    }
}
