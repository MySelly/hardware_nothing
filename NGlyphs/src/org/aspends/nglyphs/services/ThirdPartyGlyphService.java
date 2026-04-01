package org.aspends.nglyphs.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.nothing.thirdparty.IGlyphService;
import org.aspends.nglyphs.core.GlyphManagerV2;

public class ThirdPartyGlyphService extends Service {
    private final IGlyphService.Stub binder = new IGlyphService.Stub() {
        @Override
        public void setFrameColors(int[] iArray) {
            if (iArray == null || iArray.length == 0)
                return;
            GlyphManagerV2.getInstance().setFrame(iArray);
        }

        @Override
        public void openSession() {
            GlyphManagerV2.getInstance().setFrame(new int[15]);
        }

        @Override
        public void closeSession() {
            GlyphManagerV2.getInstance().setFrame(new int[15]);
        }

        @Override
        public boolean register(String str) {
            return true;
        }

        @Override
        public boolean registerSDK(String str1, String str2) {
            return true;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        GlyphManagerV2.getInstance().init(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
