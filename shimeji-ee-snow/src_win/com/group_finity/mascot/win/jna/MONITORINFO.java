package com.group_finity.mascot.win.jna;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.NativeLong;
import com.sun.jna.Structure;

/**
 *
 * @author Kilkakon
 */
public class MONITORINFO extends Structure
{
    public NativeLong cbSize;
    public RECT rcMonitor;
    public RECT rcWork;
    public NativeLong dwFlags;

    @Override
    protected List<String> getFieldOrder( )
    {
        return Arrays.asList( "cbSize", "rcMonitor", "rcWork", "dwFlags" );
    }
}
