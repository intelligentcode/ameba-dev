package ameba.dev;

import ameba.container.Container;
import ameba.core.Application;
import ameba.core.event.RequestEvent;
import ameba.event.Listener;
import ameba.feature.AmebaFeature;
import ameba.i18n.Messages;
import ameba.message.filtering.LoggingFilter;
import org.glassfish.hk2.api.ServiceLocator;

import javax.inject.Inject;
import javax.ws.rs.core.FeatureContext;
import java.util.logging.Logger;

/**
 * @author icode
 */
public class DevFeature extends AmebaFeature {
    @Inject
    private Application app;
    @Inject
    private ServiceLocator locator;

    @Override
    public boolean configure(FeatureContext context) {
        if (app.getMode().isDev()) {
            subscribeEvent(RequestEvent.class, ReloadRequestListener.class);
            if (!app.isInitialized()) {
                subscribeSystemEvent(Container.StartupEvent.class, new Listener<Container.StartupEvent>() {
                    @Override
                    public void onReceive(Container.StartupEvent event) {
                        Thread.currentThread().setContextClassLoader(app.getClassLoader());
                        final ReloadRequestListener listener = locator.createAndInitialize(ReloadRequestListener.class);
                        final ReloadRequestListener.Reload reload = listener.scanChanges();
                        if (reload.needReload && reload.classes != null && reload.classes.size() > 0) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        AmebaFeature.publishEvent(new ClassReloadEvent(reload.classes));
                                        listener.reload(reload.classes, ReloadRequestListener._classLoader);
                                    } catch (Throwable e) {
                                        logger().error(Messages.get("dev.compile.error"), e);
                                    }
                                }
                            }).start();
                        }

                        unsubscribeSystemEvent(Container.StartupEvent.class, this);
                    }
                });
            }

            context.register(new LoggingFilter(Logger.getLogger("ameba.dev.logging"), true));
        }
        return true;
    }
}
