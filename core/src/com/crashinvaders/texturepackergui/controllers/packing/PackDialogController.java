package com.crashinvaders.texturepackergui.controllers.packing;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.crashinvaders.texturepackergui.AppConstants;
import com.crashinvaders.texturepackergui.controllers.packing.processors.PackingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.PngtasticCompressingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.TinifyCompressingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.ZopfliCompressingProcessor;
import com.crashinvaders.texturepackergui.events.PackAtlasUpdatedEvent;
import com.crashinvaders.texturepackergui.services.TinifyService;
import com.crashinvaders.texturepackergui.services.model.PackModel;
import com.crashinvaders.texturepackergui.services.model.ProjectModel;
import com.crashinvaders.texturepackergui.utils.WidgetUtils;
import com.crashinvaders.texturepackergui.utils.packprocessing.CompositePackProcessor;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessingManager;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessor;
import com.github.czyzby.autumn.annotation.Initiate;
import com.github.czyzby.autumn.annotation.Inject;
import com.github.czyzby.autumn.mvc.component.i18n.LocaleService;
import com.github.czyzby.autumn.mvc.component.ui.InterfaceService;
import com.github.czyzby.autumn.mvc.stereotype.ViewDialog;
import com.github.czyzby.autumn.mvc.stereotype.ViewStage;
import com.github.czyzby.autumn.processor.event.EventDispatcher;
import com.github.czyzby.kiwi.util.common.Exceptions;
import com.github.czyzby.lml.annotation.LmlAction;
import com.github.czyzby.lml.annotation.LmlActor;
import com.github.czyzby.lml.annotation.LmlAfter;
import com.github.czyzby.lml.parser.action.ActionContainer;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.adapter.ListAdapter;
import com.kotcrab.vis.ui.widget.*;

@ViewDialog(id = "dialog_packing", value = "lml/dialogPacking.lml")
public class PackDialogController implements ActionContainer {
    private static final String PREF_KEY_AUTO_CLOSE = "auto_close_pack_dialog";

    @Inject InterfaceService interfaceService;
    @Inject EventDispatcher eventDispatcher;
    @Inject LocaleService localeService;
    @Inject TinifyService tinifyService;

    @ViewStage Stage stage;
    private Preferences prefs;

    @LmlActor("window") VisDialog window;
    @LmlActor("scrItems") VisScrollPane scrItems;
    @LmlActor("listItems") ListView.ListViewTable<PackModel> listItems;
    @LmlActor("cbAutoClose") VisCheckBox cbAutoClose;
    @LmlActor("progressBar") VisProgressBar progressBar;
    private VisImageButton btnClose;

    @Initiate
    public void initialize() {
        prefs = Gdx.app.getPreferences(AppConstants.PREF_NAME_COMMON);
    }

    @LmlAfter
    public void initView() {
        btnClose = WidgetUtils.obtainCloseButton(window);
        btnClose.setColor(new Color(0xffffff44));
        btnClose.setDisabled(true);

        stage.setScrollFocus(scrItems);

        cbAutoClose.setChecked(prefs.getBoolean(PREF_KEY_AUTO_CLOSE, false));
    }

    @LmlAction("obtainListAdapter") ListAdapter obtainListAdapter() {
        return new PackListAdapter(interfaceService);
    }

    @LmlAction("onAutoCloseChecked") void onAutoCloseChecked(VisCheckBox cbAutoClose) {
        prefs.putBoolean(PREF_KEY_AUTO_CLOSE, cbAutoClose.isChecked());
        prefs.flush();
    }

    public void launchPack(ProjectModel project, PackModel pack) {
        launchPack(project, Array.with(pack));
    }

    public void launchPack(ProjectModel project, Array<PackModel> packs) {
        PackListAdapter adapter = (PackListAdapter)listItems.getListView().getAdapter();
        adapter.clear();
        for (PackModel pack : packs) {
            adapter.add(pack);
        }

        PackProcessingManager packProcessingManager = new PackProcessingManager(
                new CompositePackProcessor(
                        new PackingProcessor(),
                        new PngtasticCompressingProcessor(),
                        new ZopfliCompressingProcessor(),
                        new TinifyCompressingProcessor(tinifyService)),
//                new TestProcessor(),
                new PackWorkerListener());

        for (int i = 0; i < packs.size; i++) {
            PackModel pack = packs.get(i);
            packProcessingManager.postPack(pack);
        }
        packProcessingManager.execute(project);
    }

    /** @return localized string */
    private String getString(String key) {
        return localeService.getI18nBundle().get(key);
    }

    /** @return localized string */
    private String getString(String key, Object... args) {
        return localeService.getI18nBundle().format(key, args);
    }

    private class PackWorkerListener implements PackProcessingManager.Listener {
        final PackListAdapter adapter;
        boolean errors = false;
        int finishedCounter = 0;

        public PackWorkerListener() {
            adapter = (PackListAdapter)listItems.getListView().getAdapter();
        }

        @Override
        public void onProcessingStarted() {
            progressBar.setRange(0, adapter.size());
            progressBar.setValue(0);
        }

        @Override
        public void onProcessingFinished() {
            btnClose.setDisabled(false);
            btnClose.setColor(Color.WHITE);

            FocusManager.switchFocus(stage, btnClose);

            window.closeOnEscape();

            if (!errors && cbAutoClose.isChecked()) {
                window.hide();
            }

            // If there is only one pack, show log on error
            if (errors && adapter.size() == 1) {
                adapter.getView(adapter.get(0)).showLogWindow();
            }

            // Indicate total result by changing progress bar color
            {
                ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle(progressBar.getStyle());
                Drawable fill = errors ?
                        VisUI.getSkin().getDrawable("progressBarErr") :
                        VisUI.getSkin().getDrawable("progressBarSucc");
                style.knob = fill;
                style.knobBefore = fill;
                progressBar.setStyle(style);
            }
        }

        @Override
        public void onBegin(PackModel pack) {
        }

        @Override
        public void onError(final PackModel pack, String log, ObjectMap metadata, Exception e) {
            onFinished(pack, log, metadata);

            adapter.getView(pack).setToError(e);
            errors = true;
        }

        @Override
        public void onSuccess(PackModel pack, String log, ObjectMap metadata) {
            onFinished(pack, log, metadata);

            adapter.getView(pack).setToSuccess();
        }

        private void onFinished(final PackModel pack, String log, ObjectMap metadata) {
            adapter.getView(pack).setLog(log);
            adapter.getView(pack).parseMetadata(metadata);

            finishedCounter += 1;
            progressBar.setValue(finishedCounter);

            // Notification
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    eventDispatcher.postEvent(new PackAtlasUpdatedEvent(pack));
                }
            });
        }
    }

    private static class TestProcessor implements PackProcessor {
        @Override
        public void processPackage(ProjectModel projectModel, PackModel packModel, ObjectMap metadata) {
            try {
                System.out.println("start processing");
                Thread.sleep(MathUtils.random(500, 2500));
                if (MathUtils.randomBoolean()) throw new RuntimeException();
                System.out.println("finish processing");
            } catch (InterruptedException e) {
                Exceptions.throwRuntimeException(e);
            }
        }
    }
}