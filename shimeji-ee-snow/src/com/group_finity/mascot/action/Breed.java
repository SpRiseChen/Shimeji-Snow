package com.group_finity.mascot.action;

import java.awt.Point;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.exception.BehaviorInstantiationException;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class Breed extends Animate
{
    private static final Logger log = Logger.getLogger( Breed.class.getName( ) );
    
    // thanks to LavenderSnek for the idea for this delegate, cleans up the breeding code nicely
    static class Delegate
    {
        public static final String PARAMETER_BORNX = "BornX";
        private static final int DEFAULT_BORNX = 0;

        public static final String PARAMETER_BORNY = "BornY";
        private static final int DEFAULT_BORNY = 0;

        public static final String PARAMETER_BORNBEHAVIOUR = "BornBehaviour";
        private static final String DEFAULT_BORNBEHAVIOUR = "";

        public static final String PARAMETER_BORNMASCOT = "BornMascot";
        private static final String DEFAULT_BORNMASCOT = "";

        public static final String PARAMETER_BORNINTERVAL = "BornInterval";
        private static final int DEFAULT_BORNINTERVAL = 1;

        public static final String PARAMETER_BORNTRANSIENT = "BornTransient";
        private static final boolean DEFAULT_BORNTRANSIENT = false;

        public static final String PARAMETER_BORNCOUNT = "BornCount";
        private static final int DEFAULT_BORNCOUNT = 1;
        
        private final ActionBase action;
        
        Delegate( ActionBase action )
        {
            this.action = action;
        }
        
        /**
         * <b>自我复制已按设计关闭</b>:Breeding / Transients 两个设置项连同设置页与托盘菜单里的
         * 开关一起删除(见 devlog),所以这里恒为 false——任何一次 Breed 动作(分裂的 Divide1、
         * 拽出新个体的 PullUpShimeji1)都不会再造出新的 mascot。
         * 新个体只能由用户手动添加(托盘 More → Choose Shimeji / {@code Main.setActiveImageSets})。
         *
         * 类保留({@code conf/actions.xml} 里的拔起/分裂动作定义与全部行为条目已于 2026-10-04 一并
         * 删除,shime38~46 不再被任何动作引用,图片文件保留):BornX/BornY/BornBehaviour/BornCount 等
         * 参数的数据路径完好,第三方 mod 若确实需要复制,可以自带一个不查此开关的动作类或覆盖配置。
         */
        boolean isEnabled( )
        {
            return false;
        }

        boolean isIntervalFrame( ) throws VariableException
        {
            return action.getTime( ) % getBornInterval( ) == 0;
        }
        
        boolean isPenultimateFrame( ) throws VariableException
        {
            return action.getTime( ) == action.getAnimation( ).getDuration( ) - 1;
        }
        
        void breed( ) throws VariableException
        {
            double scaling = Double.parseDouble( Main.getInstance( ).getProperties( ).getProperty( "Scaling", "1.0" ) );
            String childType = Main.getInstance( ).getConfiguration( getBornMascot( ) ) != null ? getBornMascot( ) : action.getMascot( ).getImageSet( );
        
            for( int index = 0; index < getBornCount( ); index++ )
            {
                final Mascot mascot = new Mascot( childType );

                log.log( Level.INFO, "Breed Mascot ({0},{1},{2})", new Object[] { action.getMascot( ), action, mascot } );

                if( action.getMascot( ).isLookRight( ) )
                {
                    mascot.setAnchor( new Point( action.getMascot( ).getAnchor( ).x - ( (int)Math.round( getBornX( ) * scaling ) ),
                                                 action.getMascot( ).getAnchor( ).y + ( (int)Math.round( getBornY( ) * scaling ) ) ) );
                }
                else
                {
                    mascot.setAnchor( new Point( action.getMascot( ).getAnchor( ).x + ( (int)Math.round( getBornX( ) * scaling ) ), 
                                                 action.getMascot( ).getAnchor( ).y + ( (int)Math.round( getBornY( ) * scaling ) ) ) );
                }
                mascot.setLookRight( action.getMascot( ).isLookRight( ) );

                try
                {
                    mascot.setBehavior( Main.getInstance( ).getConfiguration( childType ).buildBehavior( getBornBehaviour( ), action.getMascot( ) ) );
                    action.getMascot( ).getManager( ).add( mascot );
                }
                catch( final BehaviorInstantiationException e )
                {
                    log.log( Level.SEVERE, "Fatal Exception", e );
                    Main.showError( Main.getInstance( ).getLanguageBundle( ).getString( "FailedCreateNewShimejiErrorMessage" ), e );
                    mascot.dispose( );
                }
                catch( final CantBeAliveException e )
                {
                    log.log( Level.SEVERE, "Fatal Exception", e );
                    Main.showError( Main.getInstance( ).getLanguageBundle( ).getString( "FailedCreateNewShimejiErrorMessage" ), e );
                    mascot.dispose( );
                }
            }
        }

        void validateBornCount( ) throws VariableException
        {
            if( getBornCount( ) < 1 )
                throw new VariableException( "BornCount must be positive" );
        }

        void validateBornInterval( ) throws VariableException
        {
            if( getBornInterval( ) < 1 )
                throw new VariableException( "BornInterval must be positive" );
        }

        private int getBornX( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNX ), Number.class, DEFAULT_BORNX ).intValue( );
        }

        private int getBornY( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNY ), Number.class, DEFAULT_BORNY ).intValue( );
        }

        private String getBornBehaviour( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNBEHAVIOUR ), String.class, DEFAULT_BORNBEHAVIOUR );
        }

        private String getBornMascot( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNMASCOT ), String.class, DEFAULT_BORNMASCOT );
        }

        private boolean getBornTransient( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNTRANSIENT ), Boolean.class, DEFAULT_BORNTRANSIENT );
        }

        private int getBornInterval( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNINTERVAL ), Number.class, DEFAULT_BORNINTERVAL ).intValue( );
        }

        private int getBornCount( ) throws VariableException
        {
            return action.eval( action.getSchema( ).getString( PARAMETER_BORNCOUNT ), Number.class, DEFAULT_BORNCOUNT ).intValue( );
        }
    }
    
    private final Delegate delegate = new Delegate( this );
    
    public Breed( java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap context )
    {
        super( schema, animations, context );
    }

    @Override
    public void init( final Mascot mascot ) throws VariableException
    {
        super.init( mascot );
        
        delegate.validateBornCount( );
    }

    @Override
    protected void tick( ) throws LostGroundException, VariableException
    {
        super.tick( );

        if( delegate.isPenultimateFrame( ) && delegate.isEnabled( ) )
            delegate.breed( );
    }
}
