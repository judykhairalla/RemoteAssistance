package com.example.remoteassistance.helpers;

import com.example.remoteassistance.ClientActivity;
import com.example.remoteassistance.rendering.ObjectRenderer;
import com.google.ar.core.Anchor;

public class VirtualObject {
    private static final String TAG = ClientActivity.class.getSimpleName();
    private ObjectRenderer objectRenderer = new ObjectRenderer();
    private Anchor anchor;
    private float scale;
    private int type;

    public VirtualObject(ObjectRenderer objectRenderer, Anchor anchor, float scale, int type){
        this.objectRenderer = objectRenderer;
        this.anchor = anchor;
        this.scale = scale;
        this.type = type;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public float getScale() {
        return scale;
    }

    public int getType() {
        return type;
    }

    private float[] getAnchorMatrix(){
        float[] mAnchorMatrix = new float[16];
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        getAnchor().getPose().toMatrix(mAnchorMatrix, 0);

        return mAnchorMatrix;
    }

    public void render(float[] viewmtx, float[] projmtx, float lightIntensity){
        objectRenderer.updateModelMatrix(getAnchorMatrix(), getScale());
        //mVirtualObjectShadow.updateModelMatrix(mAnchorMatrix, scaleFactor);
        objectRenderer.draw(viewmtx, projmtx, lightIntensity);
        //mVirtualObjectShadow.draw(viewmtx, projmtx, lightIntensity);
    }


}
