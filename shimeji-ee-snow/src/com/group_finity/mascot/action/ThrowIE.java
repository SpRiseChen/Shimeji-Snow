package com.group_finity.mascot.action;

import com.group_finity.mascot.Main;
import java.awt.Point;
import java.util.List;
import java.util.logging.Logger;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class ThrowIE extends Animate
{
    private static final Logger log = Logger.getLogger( ThrowIE.class.getName( ) );

    public static final String PARAMETER_INITIALVX = "InitialVX";

    private static final int DEFAULT_INITIALVX = 32;

    public static final String PARAMETER_INITIALVY = "InitialVY";

    private static final int DEFAULT_INITIALVY = -10;

    public static final String PARAMETER_GRAVITY = "Gravity";

    private static final double DEFAULT_GRAVITY = 0.5;

    public ThrowIE( java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap params )
    {
        super( schema, animations, params );
    }

    /** 白名单多窗口:动作开始时钉住自己认领的窗口,扔的就是它(Z 序变化不换手)。 */
    private Area ie;

    @Override
    public void init( final Mascot mascot ) throws VariableException
    {
        super.init( mascot );
        ie = getWindowTarget();
    }

    @Override
    public boolean hasNext( ) throws VariableException
    {
        if( !Boolean.parseBoolean( Main.getInstance( ).getProperties( ).getProperty( "Throwing", "true" ) ) )
            return false;

        if( ie == null )
            ie = getWindowTarget();

        final boolean ieVisible = ie.isVisible( );

        return super.hasNext( ) && ieVisible;
    }

    @Override
    protected void tick( ) throws LostGroundException, VariableException
    {
        super.tick( );

        if( ie == null )
            ie = getWindowTarget();

        final Area activeIE = ie;

        if( activeIE.isVisible( ) )
        {
            if( getMascot( ).isLookRight( ) )
            {
                getEnvironment( ).moveIE( ie, new Point( activeIE.getLeft( ) + getInitialVx( ),
                                                           activeIE.getTop( ) + getInitialVy( ) + (int)( getTime( ) * getGravity( ) ) ) );
            }
            else
            {
                getEnvironment( ).moveIE( ie, new Point( activeIE.getLeft( ) - getInitialVx( ),
                                                           activeIE.getTop( ) + getInitialVy( ) + (int)( getTime( ) * getGravity( ) ) ) );
            }
        }
    }

    private int getInitialVx( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_INITIALVX ), Number.class, DEFAULT_INITIALVX ).intValue( );
    }

    private int getInitialVy( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_INITIALVY ), Number.class, DEFAULT_INITIALVY ).intValue( );
    }

    private double getGravity( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_GRAVITY ), Number.class, DEFAULT_GRAVITY ).doubleValue( );
    }
}
