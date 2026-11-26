package com.group_finity.mascot.action;

import java.awt.Point;
import java.util.List;
import java.util.logging.Logger;

import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class Jump extends ActionBase
{
    private static final Logger log = Logger.getLogger(Jump.class.getName());

    public static final String PARAMETER_TARGETX = "TargetX";

    private static final int DEFAULT_TARGETX = 0;

    public static final String PARAMETER_TARGETY = "TargetY";

    private static final int DEFAULT_TARGETY = 0;

    //A Pose Attribute is already named Velocity
    public static final String PARAMETER_VELOCITY = "VelocityParam";

    private static final double DEFAULT_VELOCITY = 20.0;

    public static final String VARIABLE_VELOCITYX = "VelocityX";

    public static final String VARIABLE_VELOCITYY = "VelocityY";

    public Jump( java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap context )
    {
        super( schema, animations, context );
    }

    @Override
    public boolean hasNext( ) throws VariableException
    {
        if (screenChanged()) return false;
        final int targetX = getTargetX( );
        final int targetY = getTargetY( );

        final double distanceX = targetX - getMascot( ).getAnchor( ).x;
        final double distanceY = targetY - getMascot( ).getAnchor( ).y - Math.abs( distanceX ) / 2;

        final double distance = Math.sqrt( distanceX * distanceX + distanceY * distanceY );

        return super.hasNext( ) && ( distance != 0 );
    }

    @Override
    protected void tick( ) throws LostGroundException, VariableException
    {
        final int targetX = getTargetX( );
        final int targetY = getTargetY( );

        // A carried window cannot follow a jump into another coordinate system.
        if (hasWindowTarget() && (!getWindowTarget().isVisible()
                || !getEnvironment().getWorkArea().contains(targetX, targetY))) {
            throw new LostGroundException();
        }
        final Point before = new Point(getMascot().getAnchor());
        if (before.x != targetX) getMascot().setLookRight(before.x < targetX);

        final double distanceX = targetX - getMascot( ).getAnchor( ).x;
        final double distanceY = targetY - getMascot( ).getAnchor( ).y - Math.abs( distanceX ) / 2;

        final double distance = Math.sqrt( distanceX * distanceX + distanceY * distanceY );

        final double velocity = getVelocity( );

        if( distance != 0 )
        {
            final int velocityX = (int)( velocity * distanceX / distance );
            final int velocityY = (int)( velocity * distanceY / distance );
            
            putVariable( getSchema( ).getString( VARIABLE_VELOCITYX ), velocity * distanceX / distance );
            putVariable( getSchema( ).getString( VARIABLE_VELOCITYY ), velocity * distanceY / distance );

            getMascot( ).moveAnchor( new Point( getMascot( ).getAnchor( ).x + velocityX,
                                               getMascot( ).getAnchor( ).y + velocityY ) );
            getAnimation( ).next( getMascot( ), getTime( ) );
            if (screenChanged()) return;
        }

        if( distance <= velocity )
        {
            getMascot( ).moveAnchor( new Point( targetX, targetY ) );
        }
        if (!screenChanged() && distance != 0 && before.equals(getMascot().getAnchor())) {
            // A floor/wall can prevent reaching a target; never wait there forever.
            throw new LostGroundException();
        }
    }

    private double getVelocity( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_VELOCITY ), Number.class, DEFAULT_VELOCITY ).doubleValue( );
    }

    private int getTargetX( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_TARGETX ), Number.class, DEFAULT_TARGETX ).intValue( );
    }

    private int getTargetY( ) throws VariableException
    {
        return eval( getSchema( ).getString( PARAMETER_TARGETY ), Number.class, DEFAULT_TARGETY ).intValue( );
    }
}
