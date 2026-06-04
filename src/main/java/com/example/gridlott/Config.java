package com.example.gridlott;

public class Config{
    public static int rows = 6;
    public static int cols = 15;
    public static int floors = 1;
    public static double minDuration = 30;
    public static double maxDuration = 60;
    public static double minDelay = 0.3;
    public static double maxDelay = 0.6;
    public static double fee = 30;
    public static double perSeconds = 3600;
    public static boolean betterDotVisuals = false;
    public static boolean showPaths = false;
    public static boolean showDotGlow = false; //makes the user easily tell which vehicles are exiting or entering
    public static double speedMultiplier = 1.0;


    //so i realized that yes i set the settings to floor = 0 and it stopped the
    //the grid and everything from interacting and by the i changed to 1 floor again,
    //I noticed that the total cars indicator and totals cars in summary are out of sync
    //where the total cars indicator at the top would be firs to update and a miliseconds later, the
    //the total cars in summary then updates. Send this this to gemini ai pro to investigate

    //and the by the time every spot is full the total cars is an increment higher than the
    //the total cars summary instead of them being synced together as well.

    //also have a reset button where it references this config's values
}
