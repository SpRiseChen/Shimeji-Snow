package com.group_finity.mascot;

import java.awt.Image;
import java.awt.Point;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.group_finity.mascot.config.Configuration;
import com.group_finity.mascot.config.ConfigFiles;
import com.group_finity.mascot.config.Entry;
import com.group_finity.mascot.config.Settings;
import com.group_finity.mascot.affection.AffectionCard;
import com.group_finity.mascot.affection.AffectionManager;
import com.group_finity.mascot.exception.BehaviorInstantiationException;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.gesture.HeadPatDirector;
import com.group_finity.mascot.image.ImagePairs;
import com.group_finity.mascot.imagesetchooser.ImageSetChooser;
import com.group_finity.mascot.menu.TrayUI;
import com.group_finity.mascot.mod.LanguageBundleControl;
import com.group_finity.mascot.mod.ModOverlay;
import com.group_finity.mascot.sound.Sounds;
import com.group_finity.mascot.ui.ThemeInstaller;

/**
 * Program entry point.
 *
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class Main
{
    private static final Logger log = Logger.getLogger( Main.class.getName( ) );
    // Action that matches the "Gather Around Mouse!" context menu command
    public static final String BEHAVIOR_GATHER = "ChaseMouse";

    /** 阶段 3d:外置主题装载器见 {@link ThemeInstaller}(conf/theme + mod 皮肤)。 */

    /** FlatLaf 出厂的 defaultFont,只取一次——否则每次切主题都会在已缩放的字号上再乘一次 MenuDPI。 */
    private static java.awt.Font baseDefaultFont;

    static
    {
        // 任何线程的未捕获异常都落 %TEMP%\ShimejiSnow_launch.log,便于双击启动失败时定位
        Thread.setDefaultUncaughtExceptionHandler( ( thread, error ) ->
            LaunchLog.write( "Uncaught exception in thread " + thread.getName( ), error ) );

        try
        {
            LogManager.getLogManager( ).readConfiguration( Main.class.getResourceAsStream( "/logging.properties" ) );
        }
        catch( final SecurityException e )
        {
            e.printStackTrace( );
        }
        catch( final IOException e )
        {
            e.printStackTrace( );
        }
        catch( OutOfMemoryError err )
        {
            log.log( Level.SEVERE, "Out of Memory Exception.  There are probably have too many "
                    + "Shimeji mascots in the image folder for your computer to handle.  Select fewer"
                    + " image sets or move some to the img/unused folder and try again.", err );
            Main.showError( "Out of Memory.  There are probably have too many \n"
                    + "Shimeji mascots for your computer to handle.\n"
                    + "Select fewer image sets or move some to the \n"
                    + "img/unused folder and try again." );
            System.exit( 0 );
        }
    }
    private final Manager manager = new Manager( );
    private ArrayList<String> imageSets = new ArrayList<String>( );
    private ConcurrentHashMap<String, Configuration> configurations = new ConcurrentHashMap<String, Configuration>( );
    private ConcurrentHashMap<String, ArrayList<String>> childImageSets = new ConcurrentHashMap<String, ArrayList<String>>( );
    private static Main instance = new Main( );
    private static String[ ] commandLineArgs;
    private Platform platform;
    private ResourceBundle languageBundle;

    private TrayUI trayUI;

    public static Main getInstance( )
    {
        return instance;
    }

    /** 原样保留的命令行参数(阶段 4c:--no-plugins 等开关由各子系统自查)。 */
    public static String[ ] getCommandLineArgs( )
    {
        return commandLineArgs;
    }
    private static JFrame frame = new javax.swing.JFrame( );

    public static JFrame getFrame( )
    {
        return frame;
    }

    public static void showError( String message )
    {
        JOptionPane.showMessageDialog( frame, message, "Error", JOptionPane.ERROR_MESSAGE );
    }

    public static void showError( String message, Throwable exception )
    {
        do
        {
            if( exception.getClass( ).getName( ).startsWith( "com.group_finity.mascot.exception" ) )
                message += "\n" + exception.getMessage( );
            else if( exception instanceof org.xml.sax.SAXParseException )
                message += "\n" + "Line " + ((org.xml.sax.SAXParseException)exception).getLineNumber( ) + ": " + exception.getMessage( );
            else
                message += "\n" + exception.toString( );
            exception = exception.getCause( );
        }
        while( exception != null );
        Main.showError( message + "\n" + instance.languageBundle.getString( "SeeLogForDetails" ) );
    }

    public static void main( final String[] args )
    {
        commandLineArgs = args;
        try
        {
            getInstance( ).run( );
        }
        catch( OutOfMemoryError err )
        {
            log.log( Level.SEVERE, "Out of Memory Exception.  There are probably have too many "
                    + "Shimeji mascots in the image folder for your computer to handle.  Select fewer"
                    + " image sets or move some to the img/unused folder and try again.", err );
            Main.showError( "Out of Memory.  There are probably have too many \n"
                    + "Shimeji mascots for your computer to handle.\n"
                    + "Select fewer image sets or move some to the \n"
                    + "img/unused folder and try again." );
            System.exit( 0 );
        }
        catch( final Throwable startupError )
        {
            LaunchLog.write( "Startup failed", startupError );
            try
            {
                Main.showError( startupError.toString( ) );
            }
            catch( final Throwable ignored )
            {
            }
            System.exit( 1 );
        }
    }

    public void run( )
    {
        final long bootStart = System.currentTimeMillis( );

        // test operating system
        if( !System.getProperty( "sun.arch.data.model" ).equals( "64" ) )
            platform = Platform.x86;
        else
            platform = Platform.x86_64;

        // load settings (defaults from conf/settings.default.properties + user overrides)
        Settings.getInstance( ).load( );

        // load languages(阶段 4a:文件优先 + mod 覆盖的 Control;此刻叠加层尚未装载,取的是内置文案——
        // 安全模式提示语必须先于任何 mod 内容可用)
        try
        {
            languageBundle = loadLanguageBundle( );
        }
        catch( Exception ex )
        {
            Main.showError( "The default language file could not be loaded. Ensure that you have the latest shimeji language.properties in your conf directory." );
            exit( );
            return;
        }

        // 单实例:已有实例持有 %LOCALAPPDATA%\ShimejiSnow\instance.lock 时提示并退出
        if( !SingleInstance.acquire( ) )
        {
            Main.showError( languageBundle.getString( "AlreadyRunning" ) );
            exit( );
            return;
        }

        // 崩溃自恢复(阶段 3g):上次异常退出(运行标记未清)时询问是否安全配置启动
        boolean safeMode = false;
        if( RunGuard.crashedLastRun( ) )
        {
            final int choice = javax.swing.JOptionPane.showConfirmDialog( frame,
                    languageBundle.getString( "SafeModePrompt" ),
                    languageBundle.getString( "SafeModeTitle" ),
                    javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE );
            if( choice == javax.swing.JOptionPane.YES_OPTION )
            {
                safeMode = true;
                // 安全配置:忽略用户设置层,回默认值(角色集随后固定为内置 Shimeji)
                Settings.getInstance( ).loadSafeMode( );
                languageBundle = loadLanguageBundle( );
            }
        }

        // 阶段 4a:mods 叠加层在安全模式判定之后装载——安全模式跳过全部 mod(崩溃首要嫌疑人,
        // 无 mod 基线即诊断手段);装载后重读语言包,层1整文件覆盖与层2 language.d 片段此刻才可见
        if( !safeMode )
            ModOverlay.install( );
        if( ModOverlay.isActive( ) )
        {
            ResourceBundle.clearCache( );
            languageBundle = loadLanguageBundle( );
        }

        // 阶段 4c:代码插件(叠层就绪后、首只宠物之前装载,事件从进场即可订阅;
        // --no-plugins / conf/plugins.properties 单独禁用见 PluginManager)
        com.group_finity.mascot.mod.plugin.PluginManager.loadAll( );

        // load theme (FlatLaf + conf/theme/*.properties 外置主题;旧的 conf/theme.properties 已删除,见 architecture.md §3.9)
        // handle menu size
        final int autoMenuDpi = com.group_finity.mascot.ui.MenuScaling.autoDpi( );
        if( !getProperties( ).containsKey( "MenuDPI" ) )
        {
            // containsKey 不看默认值表(Properties 继承自 Hashtable 的语义),所以自带默认值里的 MenuDPI=96
            // 不会挡住这里 —— 只有用户层真的写过才算"用户定过"。自动值扣掉了 JDK 自己那层缩放,
            // 否则 150% 屏会二次放大成 2.25×(见 MenuScaling)。
            getProperties( ).setProperty( "MenuDPI", String.valueOf( autoMenuDpi ) );
            updateConfigFile( );
        }
        else if( com.group_finity.mascot.ui.MenuScaling.isLegacyAutoDpi( getProperties( ).getProperty( "MenuDPI" ) ) )
        {
            // 历史版本把系统 DPI 原样落进用户层(150% 屏 = 144):FlatLaf 之后这就是二次放大,
            // 而 MenuDPI 在设置界面里没有入口,用户自己修不了 —— 装载时就地改成当前自动值。
            log.log( Level.INFO, "MenuDPI={0} is the legacy auto-detected screen DPI; resetting to {1} (double scaling)",
                    new Object[ ]{ getProperties( ).getProperty( "MenuDPI" ), String.valueOf( autoMenuDpi ) } );
            getProperties( ).setProperty( "MenuDPI", String.valueOf( autoMenuDpi ) );
            updateConfigFile( );
        }
        installLookAndFeel( getProperties( ).getProperty( "Theme", "scatter" ) );
        if( exitRequested.get( ) )
            return;

        // Every normal startup asks for image sets. ActiveShimeji is only used
        // by the chooser to preselect the previous selection; legacy settings
        // must not bypass this step.

        // 安全配置启动固定用内置角色集,避免损坏的用户配置进入选择循环
        if( safeMode )
        {
            imageSets.clear( );
            imageSets.add( getProperties( ).getProperty( "Recovery.ImageSet", "Acacia" ) );
        }

        do
        {
            if( imageSets.isEmpty( ) )
            {
                imageSets = new ImageSetChooser( frame, true ).display( );
                if( imageSets == null )
                {
                    exit( );
                    return;
                }
            }

            // Load shimejis
            for( int index = 0; index < imageSets.size( ); index++ )
            {
                if( loadConfiguration( imageSets.get( index ) ) == false )
                {
                    // failed validation
                    configurations.remove( imageSets.get( index ) );
                    imageSets.remove( imageSets.get( index ) );
                    index--;
                }
            }
        }
        while( imageSets.isEmpty( ) );

        // Create the tray icon
        trayUI = new TrayUI( this, frame );
        trayUI.create( );

        // Create the first mascot
        for( String imageSet : imageSets )
        {
            String informationAlreadySeen = getProperties( ).getProperty( "InformationDismissed", "" );
            if( configurations.get( imageSet ).containsInformationKey( "SplashImage" ) &&
                ( Boolean.parseBoolean( getProperties( ).getProperty( "AlwaysShowInformationScreen", "false" ) ) ||
                  !informationAlreadySeen.contains( imageSet ) ) )
            {
                InformationWindow info = new InformationWindow( );
                info.init( imageSet, configurations.get( imageSet ) );
                info.display( );
                setMascotInformationDismissed( imageSet );
                updateConfigFile( );
            }
            createMascot( imageSet );
        }

        // 手势导演(阶段 2a):摸头轮询状态机,独立于行为引擎,tick 失败自动退避
        HeadPatDirector.install( );

        // 好感度(阶段 2.2):订阅摸头事件(导演的 PatListener 缝)、启动陪伴/冷落结算,
        // 并把两行状态卡注入托盘菜单在 3e 预留的插槽;安全模式下只读不落盘
        AffectionManager.install( safeMode );
        TrayUI.setStatusCardSupplier( AffectionCard::create );

        // 全局热键(阶段 3d):conf/shortcuts.json 外置热键表,平台不支持时安静跳过
        com.group_finity.mascot.hotkey.HotkeyManager.install( );

        // 遮挡处理(阶段 3f):宠物被全屏窗口盖住时按 window_spots.properties 配置隐藏/恢复
        com.group_finity.mascot.environment.OcclusionDetector.install( );

        // 运行标记(阶段 3g):正常退出清除,残留即判定上次异常退出
        RunGuard.markRunning( );

        // 挂机观测(阶段 3g):每 10 分钟记一行宠物数与堆占用,供 24h 验收比对内存趋势
        final Thread soak = new Thread( ( ) -> {
            while( true )
            {
                try
                {
                    Thread.sleep( 10 * 60 * 1000 );
                }
                catch( final InterruptedException e )
                {
                    return;
                }
                final Runtime runtime = Runtime.getRuntime( );
                log.log( Level.INFO, "soak: mascots={0} heapUsed={1}MB heapTotal={2}MB",
                        new Object[ ]{ getManager( ).getMascots( ).size( ),
                                ( runtime.totalMemory( ) - runtime.freeMemory( ) ) >> 20,
                                runtime.totalMemory( ) >> 20 } );
            }
        }, "ShimejiSnow-Soak" );
        soak.setDaemon( true );
        soak.start( );

        // 更新检查(阶段 3g):UpdateCheck.OnStartup=true 时静默查一次
        UpdateChecker.startupCheck( );

        getManager( ).start( );
        com.group_finity.mascot.dialogue.DialogueDirector.install( );

        // 启动耗时打点(阶段 3g):配置装载/角色装载/就绪三段
        LaunchLog.write( "boot ready in " + ( System.currentTimeMillis( ) - bootStart ) + " ms ("
                + imageSets.size( ) + " set(s), safeMode=" + safeMode + ")", null );
    }

    private boolean loadConfiguration( final String imageSet )
    {
        try
        {
            // 阶段 4a:三级查找(img/<set>/conf > conf/<set> > conf)抽到 ConfigFiles;经叠加层,mod 整文件覆盖在此生效
            final Path actionsPath = ConfigFiles.actions( imageSet );

            log.log( Level.INFO, imageSet + " Read Action File ({0})", actionsPath );

            final DocumentBuilderFactory builderFactory = com.group_finity.mascot.config.SafeXml.factory();
            final Document actions = builderFactory.newDocumentBuilder( ).parse( new FileInputStream( actionsPath.toFile( ) ) );

            Configuration configuration = new Configuration( );

            configuration.load( new Entry( actions.getDocumentElement( ) ), imageSet );

            // 阶段 4b:img/<set>/conf/actions.d/*.xml 片段按应用序内存合并,不落盘;重名由后者替换并告警
            final List<Document> actionDocuments = new ArrayList<>( );
            actionDocuments.add( actions );
            for( final Path fragment : ModOverlay.listFiles( "img", imageSet, "conf", "actions.d" ) )
            {
                log.log( Level.INFO, imageSet + " Merge Action Fragment ({0})", fragment );
                final Document fragmentDocument = builderFactory.newDocumentBuilder( ).parse( new FileInputStream( fragment.toFile( ) ) );
                actionDocuments.add( fragmentDocument );
                configuration.load( new Entry( fragmentDocument.getDocumentElement( ) ), imageSet, true );
            }

            final Path behaviorsPath = ConfigFiles.behaviors( imageSet );

            log.log( Level.INFO, imageSet + " Read Behavior File ({0})", behaviorsPath );

            final Document behaviors = builderFactory.newDocumentBuilder( ).parse( new FileInputStream( behaviorsPath.toFile( ) ) );

            configuration.load( new Entry( behaviors.getDocumentElement( ) ), imageSet );

            // 阶段 4b:img/<set>/conf/behaviors.d/*.xml 片段同上
            for( final Path fragment : ModOverlay.listFiles( "img", imageSet, "conf", "behaviors.d" ) )
            {
                log.log( Level.INFO, imageSet + " Merge Behavior Fragment ({0})", fragment );
                final Document fragmentDocument = builderFactory.newDocumentBuilder( ).parse( new FileInputStream( fragment.toFile( ) ) );
                configuration.load( new Entry( fragmentDocument.getDocumentElement( ) ), imageSet, true );
            }

            final Path infoPath = ConfigFiles.info( imageSet );

            if( infoPath.toFile( ).exists( ) )
            {
                log.log( Level.INFO, imageSet + " Read Information File ({0})", infoPath );

                final Document information = com.group_finity.mascot.config.SafeXml.factory().newDocumentBuilder().parse(new FileInputStream( infoPath.toFile( ) ) );

                configuration.load( new Entry( information.getDocumentElement( ) ), imageSet );
            }

            configuration.validate( );

            configurations.put( imageSet, configuration );

            ArrayList<String> childMascots = new ArrayList<String>( );

            // born mascot bit goes here...(阶段 4b:片段里新增的 Born/Transform 同样参与登记)
            for( final Document actionDocument : actionDocuments )
            {
                for( final Entry list : new Entry( actionDocument.getDocumentElement( ) ).selectChildren( "ActionList" ) )
                {
                    for( final Entry node : list.selectChildren( "Action" ) )
                    {
                        if( node.getAttributes( ).containsKey( "BornMascot" ) )
                        {
                            String set = node.getAttribute( "BornMascot" );
                            if( !childMascots.contains( set ) )
                                childMascots.add( set );
                            if( !configurations.containsKey( set ) )
                                loadConfiguration( set );
                        }
                        if( node.getAttributes( ).containsKey( "TransformMascot" ) )
                        {
                            String set = node.getAttribute( "TransformMascot" );
                            if( !childMascots.contains( set ) )
                                childMascots.add( set );
                            if( !configurations.containsKey( set ) )
                                loadConfiguration( set );
                        }
                    }
                }
            }

            childImageSets.put( imageSet, childMascots );

            return true;
        }
        catch( final Exception e )
        {
            log.log( Level.SEVERE, "Failed to load configuration files", e );
            Main.showError( languageBundle.getString( "FailedLoadConfigErrorMessage" ), e );
        }

        return false;
    }

    /**
     * 打开设置窗口并消费其三个重载标志(环境/图片/交互窗口)。
     * 阶段 3b 的新设置中心在此处替换旧 SettingsWindow,托盘与热键共用此入口。
     */
    public void openSettingsWindow( final Image iconImage )
    {
        if( !getManager( ).isPaused( ) )
            getManager( ).togglePauseAll( ); // needed to stop the guys from potentially throwing away settings

        final com.group_finity.mascot.ui.SettingsFrame dialog = new com.group_finity.mascot.ui.SettingsFrame( this, iconImage );
        dialog.display( );

        if( dialog.getEnvironmentReloadRequired( ) )
        {
            NativeFactory.getInstance( ).getEnvironment( ).dispose( );
            NativeFactory.resetInstance( );
        }
        if( dialog.getEnvironmentReloadRequired( ) || dialog.getImageReloadRequired( ) )
        {
            // need to reload the shimeji as the images have rescaled
            boolean isExit = getManager( ).isExitOnLastRemoved( );
            getManager( ).setExitOnLastRemoved( false );
            getManager( ).disposeAll( );

            // Wipe all loaded data
            ImagePairs.clear( );
            configurations.clear( );

            // Load settings
            for( String imageSet : imageSets )
            {
                loadConfiguration( imageSet );
            }

            // Create the first mascot
            for( String imageSet : imageSets )
            {
                createMascot( imageSet );
            }

            getManager( ).setExitOnLastRemoved( isExit );
        }
        else
        {
            if( getManager( ).isPaused( ) )
                getManager( ).togglePauseAll( );
        }
        if( dialog.getInteractiveWindowReloadRequired( ) )
            NativeFactory.getInstance( ).getEnvironment( ).refreshCache( );
    }

    // Randomly creates a mascot
    public void createMascot( )
    {
        int length = imageSets.size( );
        int random = ( int ) ( length * Math.random( ) );
        createMascot( imageSets.get( random ) );
    }

    /**
     * Create a mascot
     */
    public void createMascot( String imageSet )
    {
        log.log( Level.INFO, "create a mascot" );

        // Create one mascot
        final Mascot mascot = new Mascot( imageSet );

        // Create it outside the bounds of the screen
        mascot.setAnchor( new Point( -4000, -4000 ) );

        // Randomize the initial orientation
        mascot.setLookRight( Math.random( ) < 0.5 );

        try
        {
            mascot.setBehavior( getConfiguration( imageSet ).buildNextBehavior( null, mascot ) );
            this.getManager().add( mascot );
        }
        catch( final BehaviorInstantiationException e )
        {
            log.log( Level.SEVERE, "Failed to initialize the first action", e );
            Main.showError( languageBundle.getString( "FailedInitialiseFirstActionErrorMessage" ), e );
            mascot.dispose( );
        }
        catch( final CantBeAliveException e )
        {
            log.log( Level.SEVERE, "Fatal Error", e );
            Main.showError( languageBundle.getString( "FailedInitialiseFirstActionErrorMessage" ), e );
            mascot.dispose( );
        }
        catch( Exception e )
        {
            log.log( Level.SEVERE, imageSet + " fatal error, can not be started.", e );
            Main.showError( languageBundle.getString( "CouldNotCreateShimejiErrorMessage" ) + " " + imageSet, e );
            mascot.dispose( );
        }
    }

    private void refreshLanguage( )
    {
        languageBundle = loadLanguageBundle( );

        boolean isExit = getManager( ).isExitOnLastRemoved( );
        getManager( ).setExitOnLastRemoved( false );
        getManager( ).disposeAll( );

        // Load settings
        for( String imageSet : imageSets )
        {
            loadConfiguration( imageSet );
        }

        // Create the first mascot
        for( String imageSet : imageSets )
        {
            createMascot( imageSet );
        }

        getManager( ).setExitOnLastRemoved( isExit );
    }

    /** 语言包装载的唯一入口:文件优先 + mod 覆盖的 Control(阶段 4a)。 */
    private ResourceBundle loadLanguageBundle( )
    {
        return ResourceBundle.getBundle( "language",
                // 2026-10-03 起默认语言为简体中文(语言精简:界面只提供 zh-CN/en-GB 两种)
                Locale.forLanguageTag( getProperties( ).getProperty( "Language", "zh-CN" ) ),
                new LanguageBundleControl( ) );
    }

    public void updateLanguage( String language )
    {
        if( !getProperties( ).getProperty( "Language", "zh-CN" ).equals( language ) )
        {
            getProperties( ).setProperty( "Language", language );
            refreshLanguage( );
        }
    }

    public boolean toggleBooleanSetting( String propertyName, boolean defaultValue )
    {
        if( Boolean.parseBoolean( getProperties( ).getProperty( propertyName, defaultValue + "" ) ) )
        {
            getProperties( ).setProperty( propertyName, "false" );
            return false;
        }
        else
        {
            getProperties( ).setProperty( propertyName, "true" );
            return true;
        }
    }

    private void setMascotInformationDismissed( final String imageSet )
    {
        ArrayList<String> list = new ArrayList<String>( );
        String[ ] data = getProperties( ).getProperty( "InformationDismissed", "" ).split( "/" );

        if( data.length > 0 && !data[ 0 ].equals( "" ) )
            list.addAll( Arrays.asList( data ) );
        if( !list.contains( imageSet ) )
            list.add( imageSet );

        getProperties( ).setProperty( "InformationDismissed", list.toString( ).replace( "[", "" ).replace( "]", "" ).replace( ", ", "/" ) );
    }

    public void setMascotBehaviorEnabled( final String name, final Mascot mascot, boolean enabled )
    {
        ArrayList<String> list = new ArrayList<String>( );
        String[ ] data = getProperties( ).getProperty( "DisabledBehaviours." + mascot.getImageSet( ), "" ).split( "/" );

        if( data.length > 0 && !data[ 0 ].equals( "" ) )
            list.addAll( Arrays.asList( data ) );

        if( list.contains( name ) && enabled )
            list.remove( name );
        else if( !list.contains( name ) && !enabled )
            list.add( name );

        if( list.size( ) > 0 )
            getProperties( ).setProperty( "DisabledBehaviours." + mascot.getImageSet( ), list.toString( ).replace( "[", "" ).replace( "]", "" ).replace( ", ", "/" ) );
        else
            getProperties( ).remove( "DisabledBehaviours." + mascot.getImageSet( ) );

        updateConfigFile( );
    }

    /** 唯一持久化入口,委托 Settings 层(只落用户层键)。 */
    public void updateConfigFile( )
    {
        Settings.getInstance( ).save( );
    }

    /**
     * Replaces the current set of active imageSets without modifying
     * valid imageSets that are already active. Does nothing if newImageSets
     * are null
     *
     * @param newImageSets All the imageSets that should now be active
     * @author snek, with some tweaks by Kilkakon
     * */
    public void setActiveImageSets( ArrayList<String> newImageSets )
    {
        if( newImageSets == null )
            return;

        // I don't think there would be enough imageSets chosen at any given
        // time for it to be worth using HashSet but i might be wrong
        ArrayList<String> toRemove = new ArrayList<String>( imageSets );
        toRemove.removeAll( newImageSets );

        ArrayList<String> toAdd = new ArrayList<String>( );
        ArrayList<String> toRetain = new ArrayList<String>( );
        for( String set : newImageSets )
        {
            if( !imageSets.contains( set ) )
                toAdd.add( set );
            if( !toRetain.contains( set ) )
                toRetain.add( set );
            populateArrayListWithChildSets( set, toRetain );
        }

        boolean isExit = this.getManager( ).isExitOnLastRemoved( );
        this.getManager( ).setExitOnLastRemoved( false );

        for( String r : toRemove )
            removeLoadedImageSet( r, toRetain );

        for( String a : toAdd )
            addImageSet( a );

        this.getManager( ).setExitOnLastRemoved( isExit );
    }

    private void populateArrayListWithChildSets( String imageSet, ArrayList<String> childList )
    {
        if( childImageSets.containsKey( imageSet ) )
        {
            for( String set : childImageSets.get( imageSet ) )
            {
                if( !childList.contains( set ) )
                {
                    populateArrayListWithChildSets( set, childList );
                    childList.add( set );
                }
            }
        }
    }

    private void removeLoadedImageSet( String imageSet, ArrayList<String> setsToIgnore )
    {
        if( childImageSets.containsKey( imageSet ) )
        {
            for( String set : childImageSets.get( imageSet ) )
            {
                if( !setsToIgnore.contains( set ) )
                {
                    setsToIgnore.add( set );
                    imageSets.remove( imageSet );
                    getManager( ).remainNone( imageSet );
                    configurations.remove( imageSet );
                    ImagePairs.removeAll( imageSet );
                    removeLoadedImageSet( set, setsToIgnore );
                }
            }
        }

        if( !setsToIgnore.contains( imageSet ) )
        {
            imageSets.remove( imageSet );
            getManager( ).remainNone( imageSet );
            configurations.remove( imageSet );
            ImagePairs.removeAll( imageSet );
        }
    }

    private void addImageSet( String imageSet )
    {
        if( configurations.containsKey( imageSet ) )
        {
            imageSets.add( imageSet );
            createMascot( imageSet );
        }
        else
        {
            if( loadConfiguration( imageSet ) )
            {
                imageSets.add( imageSet );
                String informationAlreadySeen = getProperties( ).getProperty( "InformationDismissed", "" );
                if( configurations.get( imageSet ).containsInformationKey( "SplashImage" ) &&
                    ( Boolean.parseBoolean( getProperties( ).getProperty( "AlwaysShowInformationScreen", "false" ) ) ||
                      !informationAlreadySeen.contains( imageSet ) ) )
                {
                    InformationWindow info = new InformationWindow( );
                    info.init( imageSet, configurations.get( imageSet ) );
                    info.display( );
                    setMascotInformationDismissed( imageSet );
                    updateConfigFile( );
                }
                createMascot( imageSet );
            }
            else
            {
                // conf failed
                configurations.remove( imageSet ); // maybe move this to the loadConfig catch
            }
        }
    }

    public Configuration getConfiguration( String imageSet )
    {
        return configurations.get( imageSet );
    }

    public List<String> getImageSets( )
    {
        return imageSets;
    }

    public Manager getManager( )
    {
        return this.manager;
    }

    public Platform getPlatform( )
    {
        return platform;
    }

    public Properties getProperties( )
    {
        return Settings.getInstance( ).properties( );
    }

    public ResourceBundle getLanguageBundle( )
    {
        return languageBundle;
    }

    /**
     * 阶段 3d:装载 L&F = FlatLaf + conf/theme 外置主题 + mod 皮肤,再按 MenuDPI 缩放 UI 字体。
     * 真正的装载与失败回退在 {@link ThemeInstaller};这里只负责把 mod 主题目录按应用序交给它,
     * 并在 FlatLaf 整体不可用(缺 jar 等)时沿用阶段 3b 之前的跨平台回退。
     * 设置中心切主题也走这条路径(SettingsFrame.applyTheme),于是顺带修掉了"切主题丢掉 MenuDPI 缩放"。
     */
    public void installLookAndFeel( final String theme )
    {
        final List<Path> modThemeDirs = new ArrayList<>( );
        for( final ModOverlay.Mod mod : ModOverlay.getActiveMods( ) )
            modThemeDirs.add( mod.path.resolve( "conf" ).resolve( ThemeInstaller.THEME_DIR ) );

        if( !ThemeInstaller.install( theme, modThemeDirs ) )
        {
            try
            {
                UIManager.setLookAndFeel( UIManager.getCrossPlatformLookAndFeelClassName( ) );
            }
            catch( final Exception ex )
            {
                log.log( Level.SEVERE, "Look & Feel unsupported.", ex );
                exit( );
            }
            return;
        }

        applyMenuFontScaling( );
    }

    /** FlatLaf 装载后按 MenuDPI 缩放 UI 默认字体(替代原 NimROD 主题字体缩放)。 */
    private void applyMenuFontScaling( )
    {
        try
        {
            final float menuScaling = com.group_finity.mascot.ui.MenuScaling.factor( getProperties( ) );
            // 自绘组件(卡片/按钮/侧边导航/菜单)按同一比例缩放,否则它们会与 Swing 控件尺寸脱节
            com.group_finity.mascot.ui.theme.Brand.setUiScale( menuScaling );
            if( baseDefaultFont == null )
                baseDefaultFont = UIManager.getFont( "defaultFont" );
            // 只缩放 FlatLaf **自带**字体,不换成 Brand 的物理字体:FlatLaf 那个是按系统区域挑的
            // (中文 Windows = Microsoft YaHei UI,能画中文),换成 Segoe UI 这类物理字体整个界面会变豆腐块
            // ——Java 的逐字回退只对逻辑字体可靠(阶段 3d 的离屏预览里踩到过)。
            if( baseDefaultFont != null && menuScaling != 1.0f )
                UIManager.put( "defaultFont", baseDefaultFont.deriveFont( (float)Math.round( baseDefaultFont.getSize( ) * menuScaling ) ) );
        }
        catch( final Exception ex )
        {
            log.log( Level.WARNING, "Could not apply MenuDPI font scaling.", ex );
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean exitRequested = new java.util.concurrent.atomic.AtomicBoolean( );

    public void exit( )
    {
        if( !exitRequested.compareAndSet( false, true ) )
            return;
        // Marshal away from Manager.tick too: stopping its own thread would deadlock.
        javax.swing.SwingUtilities.invokeLater( () ->
            com.group_finity.mascot.dialogue.DialogueDirector.goodbye( this::finishExit ) );
    }

    private void finishExit( )
    {
        // 阶段 4c:先让插件收尾(它们可能还持着宠物/监听)
        com.group_finity.mascot.mod.plugin.PluginManager.shutdownAll( );
        // 阶段 2.2:好感度最后一次落盘(退出后不再有 tick)
        AffectionManager.shutdown( );
        this.getManager( ).disposeAll( );
        this.getManager( ).stop( );
        // Stop window-moving actions before returning offscreen windows at the native boundary.
        try
        {
            NativeFactory.getInstance( ).getEnvironment( ).dispose( );
        }
        catch( final RuntimeException | LinkageError ex )
        {
            log.log( Level.WARNING, "Could not dispose desktop environment on exit", ex );
        }
        RunGuard.markCleanExit( );
        SingleInstance.release( );
        System.exit( 0 );
    }
}
