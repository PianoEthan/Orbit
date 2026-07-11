/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.renderer.android;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.ICacheManager;
import master.flame.danmaku.danmaku.model.IDanmakuIterator;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.renderer.IRenderer;
import master.flame.danmaku.danmaku.renderer.Renderer;
import master.flame.danmaku.danmaku.util.SystemClock;


public class DanmakuRenderer extends Renderer {

    private final DanmakuTimer mStartTimer = new DanmakuTimer();
    private final RenderingState mRenderingState = new RenderingState();
    private final DanmakuContext mContext;
    private DanmakusRetainer.Verifier mVerifier;
    private final DanmakusRetainer.Verifier verifier = new DanmakusRetainer.Verifier() {
        @Override
        public boolean skipLayout(BaseDanmaku danmaku, float fixedTop, int lines, boolean willHit) {
            if (danmaku.priority == 0 && mContext.mDanmakuFilters.filterSecondary(danmaku, lines, 0, mStartTimer, willHit, mContext)) {
                danmaku.setVisibility(false);
                return true;
            }
            return false;
        }
    };
    private final DanmakusRetainer mDanmakusRetainer;
    private ICacheManager mCacheManager;
    private OnDanmakuShownListener mOnDanmakuShownListener;

    private final BaseDanmaku[] mLastDrawnDanmakus = new BaseDanmaku[15];
    private int mLastDrawnIndex = 0;
    private final java.util.ArrayList<BaseDanmaku> mSpecialDanmakusToDraw = new java.util.ArrayList<>();

    private boolean isExactDuplicate(BaseDanmaku a, BaseDanmaku b) {
        if (a == b) return false;
        if (a.getType() != b.getType()) return false;
        if (Float.compare(a.getLeft(), b.getLeft()) != 0) return false;
        if (Float.compare(a.getTop(), b.getTop()) != 0) return false;
        if (a.textColor != b.textColor) return false;
        if (a.textSize != b.textSize) return false;
        if (a.text == null || !a.text.equals(b.text)) return false;
        return true;
    }

    public DanmakuRenderer(DanmakuContext config) {
        mContext = config;
        mDanmakusRetainer = new DanmakusRetainer();
    }

    @Override
    public void clear() {
        clearRetainer();
        mContext.mDanmakuFilters.clear();
    }

    @Override
    public void clearRetainer() {
        mDanmakusRetainer.clear();
    }

    @Override
    public void release() {
        mDanmakusRetainer.release();
        mContext.mDanmakuFilters.clear();
    }

    @Override
    public void setVerifierEnabled(boolean enabled) {
        mVerifier = (enabled ? verifier : null);
    }

    @Override
    public RenderingState draw(IDisplayer disp, IDanmakus danmakus, long startRenderTime) {
        int lastTotalDanmakuCount = mRenderingState.totalDanmakuCount;
        mRenderingState.reset();       
        IDanmakuIterator itr = danmakus.iterator();
        int orderInScreen = 0;        
        mStartTimer.update(SystemClock.uptimeMillis());
        int sizeInScreen = danmakus.size();
        BaseDanmaku drawItem = null;
        int specialDanmakuCount = 0;
        mSpecialDanmakusToDraw.clear();
        
        for (int i = 0; i < mLastDrawnDanmakus.length; i++) {
            mLastDrawnDanmakus[i] = null;
        }
        mLastDrawnIndex = 0;

        while (itr.hasNext()) {

            drawItem = itr.next();

            if (!drawItem.hasPassedFilter()) {
                mContext.mDanmakuFilters.filter(drawItem, orderInScreen, sizeInScreen, mStartTimer, false, mContext);
            }

            if (drawItem.time < startRenderTime
                    || (drawItem.priority == 0 && drawItem.isFiltered())) {
                continue;
            }

            if (drawItem.isLate()) {
                if (mCacheManager != null && !drawItem.hasDrawingCache()) {
                    mCacheManager.addDanmaku(drawItem);
                }
                break;
            }
            
            if (drawItem.getType() == BaseDanmaku.TYPE_SCROLL_RL
                    || drawItem.getType() == BaseDanmaku.TYPE_SCROLL_LR){
                // 同屏弹幕密度只对滚动弹幕有效
                orderInScreen++;
            } else if (drawItem.getType() == BaseDanmaku.TYPE_SPECIAL) {
                if (drawItem.isOutside()) {
                    continue;
                }
                specialDanmakuCount++;
                if (specialDanmakuCount > 50) {
                    continue;
                }
            }

            // measure
            if (!drawItem.isMeasured()) {
                drawItem.measure(disp, false);
            }

            // layout
            mDanmakusRetainer.fix(drawItem, disp, mVerifier);

            // draw
            if (!drawItem.isOutside() && drawItem.isShown()) {
                if (drawItem.lines == null && drawItem.getBottom() > disp.getHeight()) {
                    continue;    // skip bottom outside danmaku
                }
                
                boolean isDuplicate = false;
                for (BaseDanmaku cachedItem : mLastDrawnDanmakus) {
                    if (cachedItem != null && isExactDuplicate(cachedItem, drawItem)) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (isDuplicate) {
                    continue;
                }
                
                mLastDrawnDanmakus[mLastDrawnIndex] = drawItem;
                mLastDrawnIndex = (mLastDrawnIndex + 1) % mLastDrawnDanmakus.length;

                if (drawItem.getType() == BaseDanmaku.TYPE_SPECIAL) {
                    mSpecialDanmakusToDraw.add(drawItem);
                    continue;
                }

                int renderingType = drawItem.draw(disp);
                if(renderingType == IRenderer.CACHE_RENDERING) {
                    mRenderingState.cacheHitCount++;
                } else if(renderingType == IRenderer.TEXT_RENDERING) {
                    mRenderingState.cacheMissCount++;
                    if (mCacheManager != null) {
                        mCacheManager.addDanmaku(drawItem);
                    }
                }
                mRenderingState.addCount(drawItem.getType(), 1);
                mRenderingState.addTotalCount(1);

                if (mOnDanmakuShownListener != null
                        && drawItem.firstShownFlag != mContext.mGlobalFlagValues.FIRST_SHOWN_RESET_FLAG) {
                    drawItem.firstShownFlag = mContext.mGlobalFlagValues.FIRST_SHOWN_RESET_FLAG;
                    mOnDanmakuShownListener.onDanmakuShown(drawItem);
                }
            }

        }

        for (int i = 0; i < mSpecialDanmakusToDraw.size(); i++) {
            BaseDanmaku specialItem = mSpecialDanmakusToDraw.get(i);
            int renderingType = specialItem.draw(disp);
            if(renderingType == IRenderer.CACHE_RENDERING) {
                mRenderingState.cacheHitCount++;
            } else if(renderingType == IRenderer.TEXT_RENDERING) {
                mRenderingState.cacheMissCount++;
                if (mCacheManager != null) {
                    mCacheManager.addDanmaku(specialItem);
                }
            }
            mRenderingState.addCount(specialItem.getType(), 1);
            mRenderingState.addTotalCount(1);

            if (mOnDanmakuShownListener != null
                    && specialItem.firstShownFlag != mContext.mGlobalFlagValues.FIRST_SHOWN_RESET_FLAG) {
                specialItem.firstShownFlag = mContext.mGlobalFlagValues.FIRST_SHOWN_RESET_FLAG;
                mOnDanmakuShownListener.onDanmakuShown(specialItem);
            }
        }
        
        mRenderingState.nothingRendered = (mRenderingState.totalDanmakuCount == 0);
        mRenderingState.endTime = drawItem != null ? drawItem.time : RenderingState.UNKNOWN_TIME;
        if (mRenderingState.nothingRendered) {
            mRenderingState.beginTime = RenderingState.UNKNOWN_TIME;
        }
        mRenderingState.incrementCount = mRenderingState.totalDanmakuCount - lastTotalDanmakuCount;
        mRenderingState.consumingTime = mStartTimer.update(SystemClock.uptimeMillis());
        return mRenderingState;
    }

    public void setCacheManager(ICacheManager cacheManager) {
        mCacheManager = cacheManager;
    }

    @Override
    public void setOnDanmakuShownListener(OnDanmakuShownListener onDanmakuShownListener) {
        mOnDanmakuShownListener = onDanmakuShownListener;
    }

    @Override
    public void removeOnDanmakuShownListener() {
        mOnDanmakuShownListener = null;
    }
}
