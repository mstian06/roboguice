package roboguice.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.event.EventManager;
import roboguice.event.ObservesTypeListener;
import roboguice.event.eventListener.factory.EventListenerThreadingDecorator;
import roboguice.fragment.FragmentUtil;
import roboguice.inject.AccountManagerProvider;
import roboguice.inject.AssetManagerProvider;
import roboguice.inject.ContentResolverProvider;
import roboguice.inject.ContextScope;
import roboguice.inject.ContextScopedSystemServiceProvider;
import roboguice.inject.ContextSingleton;
import roboguice.inject.ExtrasListener;
import roboguice.inject.HandlerProvider;
import roboguice.inject.NullProvider;
import roboguice.inject.PreferenceListener;
import roboguice.inject.ResourceListener;
import roboguice.inject.ResourcesProvider;
import roboguice.inject.SharedPreferencesProvider;
import roboguice.inject.SystemServiceProvider;
import roboguice.inject.ViewListener;
import roboguice.service.RoboService;
import roboguice.util.Ln;
import roboguice.util.LnImpl;
import roboguice.util.LnInterface;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

/**
 * A Module that provides bindings and configuration to use Guice on Android.
 * Used by {@link roboguice.RoboGuice}.
 *
 * If you wish to add your own bindings, DO NOT subclass this class.  Instead, create a new
 * module that extends AbstractModule with your own bindings, then do something like the following:
 *
 * RoboGuice.setAppliationInjector( app, RoboGuice.DEFAULT_STAGE, Modules.override(RoboGuice.newDefaultRoboModule(app)).with(new MyModule() );
 *
 * @see com.google.inject.util.Modules#override(com.google.inject.Module...)
 * @see roboguice.RoboGuice#setBaseApplicationInjector(android.app.Application, com.google.inject.Stage, com.google.inject.Module...)
 * @see roboguice.RoboGuice#newDefaultRoboModule(android.app.Application)
 * @see roboguice.RoboGuice#DEFAULT_STAGE
 *
 * @author Mike Burton
 */
public class DefaultRoboModule extends AbstractModule {
    public static final String GLOBAL_EVENT_MANAGER_NAME = "GlobalEventManager";

    @SuppressWarnings("rawtypes")
	protected static final Class accountManagerClass;

    @SuppressWarnings("rawtypes")
    private static Map<Class, String> mapSystemSericeClassToName = new HashMap<Class, String>();

    private AnnotationDatabaseFinder annotationDatabaseFinder;
    private Set<String> injectableClasses;

    static {
        Class<?> c = null;
        try {
            c = Class.forName("android.accounts.AccountManager");
        } catch( Throwable ignored ) {}
        accountManagerClass = c;
        
        mapSystemSericeClassToName.put(LocationManager.class, Context.LOCATION_SERVICE);
        mapSystemSericeClassToName.put(WindowManager.class, Context.WINDOW_SERVICE);
        mapSystemSericeClassToName.put(ActivityManager.class, Context.ACTIVITY_SERVICE);
        mapSystemSericeClassToName.put(PowerManager.class, Context.POWER_SERVICE);
        mapSystemSericeClassToName.put(AlarmManager.class, Context.ALARM_SERVICE);
        mapSystemSericeClassToName.put(NotificationManager.class, Context.NOTIFICATION_SERVICE);
        mapSystemSericeClassToName.put(KeyguardManager.class, Context.KEYGUARD_SERVICE);
        mapSystemSericeClassToName.put(Vibrator.class, Context.VIBRATOR_SERVICE);
        mapSystemSericeClassToName.put(ConnectivityManager.class, Context.CONNECTIVITY_SERVICE);
        mapSystemSericeClassToName.put(WifiManager.class, Context.WIFI_SERVICE);
        mapSystemSericeClassToName.put(InputMethodManager.class, Context.INPUT_METHOD_SERVICE);
        mapSystemSericeClassToName.put(SensorManager.class, Context.SENSOR_SERVICE);
        mapSystemSericeClassToName.put(TelephonyManager.class, Context.TELEPHONY_SERVICE);
        mapSystemSericeClassToName.put(AudioManager.class, Context.ACCESSIBILITY_SERVICE);
    }

    protected Application application;
    protected ContextScope contextScope;
    protected ResourceListener resourceListener;
    protected ViewListener viewListener;

    @SuppressWarnings("rawtypes")
    private AnnotatedBindingBuilder noOpAnnotatedBindingBuilder = new NoOpAnnotatedBindingBuilder();



    public DefaultRoboModule(final Application application, ContextScope contextScope, ViewListener viewListener, ResourceListener resourceListener) {
        this.application = application;
        this.contextScope = contextScope;
        this.viewListener = viewListener;
        this.resourceListener = resourceListener;
        try {
            annotationDatabaseFinder = new AnnotationDatabaseFinder(application);
            RoboGuice.setAnnotationDatabaseFinder(annotationDatabaseFinder);
            injectableClasses = annotationDatabaseFinder.getInjectedClasses();
        } catch(Exception ex ) {
            injectableClasses = new HashSet<String>();
            ex.printStackTrace();
        }
    }

    /**
     * Configure this module to define Android related bindings.
     */
    @Override
    protected void configure() {

        final Provider<Context> contextProvider = getProvider(Context.class);
        final ExtrasListener extrasListener = new ExtrasListener(contextProvider);
        final PreferenceListener preferenceListener = new PreferenceListener(contextProvider,application,contextScope);
        final EventListenerThreadingDecorator observerThreadingDecorator = new EventListenerThreadingDecorator();

        // Singletons
        bind(ViewListener.class).toInstance(viewListener);
        bind(PreferenceListener.class).toInstance(preferenceListener);

        // ContextSingleton bindings
        bindScope(ContextSingleton.class, contextScope);
        bind(ContextScope.class).toInstance(contextScope);
        bind(AssetManager.class).toProvider(AssetManagerProvider.class);
        bind(Context.class).toProvider(NullProvider.<Context>instance()).in(ContextSingleton.class);
        bind(Activity.class).toProvider(NullProvider.<Activity>instance()).in(ContextSingleton.class);
        bind(RoboActivity.class).toProvider(NullProvider.<RoboActivity>instance()).in(ContextSingleton.class);
        bind(Service.class).toProvider(NullProvider.<Service>instance()).in(ContextSingleton.class);
        bind(RoboService.class).toProvider(NullProvider.<RoboService>instance()).in(ContextSingleton.class);
        
        // Sundry Android Classes
        bind(SharedPreferences.class).toProvider(SharedPreferencesProvider.class);
        bind(Resources.class).toProvider(ResourcesProvider.class);
        bind(ContentResolver.class).toProvider(ContentResolverProvider.class);
        bind(Application.class).toInstance(application);
        bind(EventListenerThreadingDecorator.class).toInstance(observerThreadingDecorator);
        bind(Handler.class).toProvider(HandlerProvider.class);

        // System Services
        bindSystemService(LocationManager.class);
        bindSystemService(WindowManager.class);
        bindSystemService(ActivityManager.class);
        bindSystemService(PowerManager.class);
        bindSystemService(AlarmManager.class);
        bindSystemService(NotificationManager.class);
        bindSystemService(KeyguardManager.class);
        bindSystemService(Vibrator.class);
        bindSystemService(ConnectivityManager.class);
        bindSystemService(WifiManager.class);
        bindSystemService(InputMethodManager.class);
        bindSystemService(SensorManager.class);
        bindSystemService(TelephonyManager.class);
        bindSystemService(AudioManager.class);
        
        // System Services that must be scoped to current context
        bind(LayoutInflater.class).toProvider(new ContextScopedSystemServiceProvider<LayoutInflater>(contextProvider,Context.LAYOUT_INFLATER_SERVICE));
        bind(SearchManager.class).toProvider(new ContextScopedSystemServiceProvider<SearchManager>(contextProvider,Context.SEARCH_SERVICE));

        // Android Resources, Views and extras require special handling
        bindListener(Matchers.any(), resourceListener);
        bindListener(Matchers.any(), extrasListener);
        bindListener(Matchers.any(), viewListener);
        bindListener(Matchers.any(), preferenceListener);
        bindListener(Matchers.any(), new ObservesTypeListener(getProvider(EventManager.class), observerThreadingDecorator));

        bind(LnInterface.class).to(LnImpl.class);

        requestInjection(observerThreadingDecorator);

        requestStaticInjection(Ln.class);

        bindDynamicBindings();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
        if( injectableClasses.isEmpty() || injectableClasses.contains(clazz.getName()) )
            return super.bind(clazz);
        else
            return noOpAnnotatedBindingBuilder;
    }

    @SuppressWarnings("unchecked")
	private void bindDynamicBindings() {
		// Compatibility library bindings
        if(FragmentUtil.hasSupport) {
            bind(FragmentUtil.supportFrag.fragmentManagerType()).toProvider(FragmentUtil.supportFrag.fragmentManagerProviderType());
        }
        if(FragmentUtil.hasNative) {
            bind(FragmentUtil.nativeFrag.fragmentManagerType()).toProvider(FragmentUtil.nativeFrag.fragmentManagerProviderType());
        }

        // 2.0 Eclair
        if( VERSION.SDK_INT>=5 ) {
            bind(accountManagerClass).toProvider(AccountManagerProvider.class);
        }
	}
    


    @Provides
    @Singleton
    public PackageInfo providesPackageInfo() {
        try {
            return application.getPackageManager().getPackageInfo(application.getPackageName(),0);
        } catch( PackageManager.NameNotFoundException e ) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Named(Settings.Secure.ANDROID_ID)
    public String providesAndroidId() {
        String androidId = null;
        final ContentResolver contentResolver = application.getContentResolver();
        try {
            androidId = Secure.getString(contentResolver, Secure.ANDROID_ID);
        } catch( RuntimeException e) {
            // ignore Stub! errors for Secure.getString() when mocking in test cases since there's no way to mock static methods
            Log.e(DefaultRoboModule.class.getName(), "Impossible to get the android device Id. This may fail 'normally' when testing.", e);
        }

        if(!androidId.equals("")) {
            return androidId;
        } else {
            throw new RuntimeException("No Android Id.");
        }
    }

    @Provides
    @Named(GLOBAL_EVENT_MANAGER_NAME)
    @Singleton
    public EventManager providesGlobalEventManager() {
        return new EventManager();
    }

    private <T> void bindSystemService(Class<T> c) {
        if( injectableClasses.isEmpty() || injectableClasses.contains(c.getName()) ) {
            bind(c).toProvider(new SystemServiceProvider<T>(application, mapSystemSericeClassToName.get(c) ));
        }

    }

}
