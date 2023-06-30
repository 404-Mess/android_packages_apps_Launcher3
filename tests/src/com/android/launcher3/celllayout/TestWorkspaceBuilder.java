/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.ui.TestViewHelpers.findWidgetProvider;
import static com.android.launcher3.util.WidgetUtils.createWidgetInfo;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import java.util.function.Supplier;
import java.util.stream.IntStream;

public class TestWorkspaceBuilder {

    private static final String TAG = "CellLayoutBoardBuilder";
    private static final ComponentName APP_COMPONENT_NAME = new ComponentName(
            "com.google.android.calculator", "com.android.calculator2.Calculator");

    private UserHandle mMyUser;

    private Context mContext;
    private ContentResolver mResolver;

    public TestWorkspaceBuilder(Context context) {
        mMyUser = Process.myUserHandle();
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    /**
     * Fills the given rect in WidgetRect with 1x1 widgets. This is useful to equalize cases.
     */
    private FavoriteItemsTransaction fillWithWidgets(CellLayoutBoard.WidgetRect widgetRect,
            FavoriteItemsTransaction transaction, int screenId) {
        int initX = widgetRect.getCellX();
        int initY = widgetRect.getCellY();
        for (int x = initX; x < initX + widgetRect.getSpanX(); x++) {
            for (int y = initY; y < initY + widgetRect.getSpanY(); y++) {
                try {
                    // this widgets are filling, we don't care if we can't place them
                    transaction.addItem(createWidgetInCell(
                            new CellLayoutBoard.WidgetRect(CellLayoutBoard.CellType.IGNORE,
                                    new Rect(x, y, x, y)), screenId));
                } catch (Exception e) {
                    Log.d(TAG, "Unable to place filling widget at " + x + "," + y);
                }
            }
        }
        return transaction;
    }

    private AppInfo getApp() {
        return new AppInfo(APP_COMPONENT_NAME, "test icon", mMyUser,
                AppInfo.makeLaunchIntent(APP_COMPONENT_NAME));
    }

    private void addCorrespondingWidgetRect(CellLayoutBoard.WidgetRect widgetRect,
            FavoriteItemsTransaction transaction, int screenId) {
        if (widgetRect.mType == 'x') {
            fillWithWidgets(widgetRect, transaction, screenId);
        } else {
            transaction.addItem(createWidgetInCell(widgetRect, screenId));
        }
    }

    /**
     * Builds the given board into the transaction
     */
    public FavoriteItemsTransaction buildFromBoard(CellLayoutBoard board,
            FavoriteItemsTransaction transaction, final int screenId) {
        board.getWidgets().forEach(
                (widgetRect) -> addCorrespondingWidgetRect(widgetRect, transaction, screenId));
        board.getIcons().forEach((iconPoint) ->
                transaction.addItem(() -> createIconInCell(iconPoint, screenId))
        );
        board.getFolders().forEach((folderPoint) ->
                transaction.addItem(() -> createFolderInCell(folderPoint, screenId))
        );
        return transaction;
    }

    /**
     * Fills the hotseat row with apps instead of suggestions, for this to work the workspace should
     * be clean otherwise this doesn't overrides the existing icons.
     */
    public FavoriteItemsTransaction fillHotseatIcons(FavoriteItemsTransaction transaction) {
        IntStream.range(0, InvariantDeviceProfile.INSTANCE.get(mContext).numDatabaseHotseatIcons)
                .forEach(i -> transaction.addItem(() -> getHotseatValues(i)));
        return transaction;
    }

    private Supplier<ItemInfo> createWidgetInCell(
            CellLayoutBoard.WidgetRect widgetRect, int screenId) {
        // Create the widget lazily since the appWidgetId can get lost during setup
        return () -> {
            LauncherAppWidgetProviderInfo info = findWidgetProvider(false);
            LauncherAppWidgetInfo item = createWidgetInfo(info, getApplicationContext(), true);
            item.cellX = widgetRect.getCellX();
            item.cellY = widgetRect.getCellY();
            item.spanX = widgetRect.getSpanX();
            item.spanY = widgetRect.getSpanY();
            item.screenId = screenId;
            return item;
        };
    }

    public FolderInfo createFolderInCell(CellLayoutBoard.FolderPoint folderPoint, int screenId) {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.screenId = screenId;
        folderInfo.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        folderInfo.cellX = folderPoint.coord.x;
        folderInfo.cellY = folderPoint.coord.y;
        folderInfo.minSpanY = folderInfo.minSpanX = folderInfo.spanX = folderInfo.spanY = 1;
        folderInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true, null);

        for (int i = 0; i < folderPoint.getNumberIconsInside(); i++) {
            folderInfo.add(getDefaultWorkspaceItem(screenId), false);
        }

        return folderInfo;
    }

    private WorkspaceItemInfo getDefaultWorkspaceItem(int screenId) {
        WorkspaceItemInfo item = new WorkspaceItemInfo(getApp());
        item.screenId = screenId;
        item.minSpanY = item.minSpanX = item.spanX = item.spanY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        return item;
    }

    private ItemInfo createIconInCell(CellLayoutBoard.IconPoint iconPoint, int screenId) {
        WorkspaceItemInfo item = new WorkspaceItemInfo(getApp());
        item.screenId = screenId;
        item.cellX = iconPoint.getCoord().x;
        item.cellY = iconPoint.getCoord().y;
        item.minSpanY = item.minSpanX = item.spanX = item.spanY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        return item;
    }

    private ItemInfo getHotseatValues(int x) {
        WorkspaceItemInfo item = new WorkspaceItemInfo(getApp());
        item.cellX = x;
        item.cellY = 0;
        item.minSpanY = item.minSpanX = item.spanX = item.spanY = 1;
        item.rank = x;
        item.screenId = x;
        item.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        return item;
    }
}
