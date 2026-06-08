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
    public static boolean showDotGlow = false; //makes the user easily tell which vehicles are exiting or entering//
    public static double speedMultiplier = 1.0;


    //so i realized that yes i set the settings to floor = 0 and it stopped the
    //the grid and everything from interacting and by the i changed to 1 floor again,
    //I noticed that the total cars indicator and totals cars in summary are out of sync
    //where the total cars indicator at the top would be firs to update and a miliseconds later, the
    //the total cars in summary then updates. Send this this to gemini ai pro to investigate

    //and the by the time every spot is full the total cars is an increment higher than the
    //the total cars summary instead of them being synced together as well.

    //also have a reset button where it references this config's values

    //==================================================================================================================

    //ALSO what I realized is the speed the of dot traversing and the refresh updater for vehicle finder, so i might
    //have to make the vehicle finder refresh update accommodate with the relative dot speed and the spawn times too
    //
    // so for example,

    //the higher the dot speed multiplier and the lower the spawn time set in the settings, the faster the refresh
    // update cycles, Same for the low dot speed multiplier and higher spawn time but the slower the refresh update cycles

    //==================================================================================================================

    //AND ALSO when the dot disappears on the vehicle finder depending on search query/category chosen, it essentially
    //means that it doesnt have a hold of the occupancy[][][] since its disappearance means its null in that position

    //so we gotta make it so that we can still track them down until they actually leave the lot and before they enter
    // their cells and then it will then disappear in the vehicle finder refresh tab

    //the solution is to when the occupancy[floor][row][col] turns null (also meaning other vehicles to reserve that spot),
    //we need to then put that vehicle object that left into another list so that the vehicle finder refresh tab
    //still has that vehicle (since its still in the lot even while exiting from cell and entering to cell)
    // list of transitionToCell and list of transitionFromCell

    // and also track the vehicle once chosen followed by colorful indications of:
    //THIS ONLY APPLIES FOR ANY CATEGORY EXCEPT CELLS SINCE IT CONCEPTUALLY DOESN'T MAKE SENSE TO TRACK STATUS ON A PARKING SPOT

    //TRANSITING (green) |   PARKED (gray)   | EXITING (yellow or orange)
    //     ^                   ^                  ^
    //toCell = true;     | toCell = false;   | toCell = false;
    //parked = false;    | parked = true;    | parked = false;
    //exitCell = false   | exitCell = false; | exitCell = true;

    //or

    //if(!parked && !exitCell) | if(!toCell && !exitCell) | if(!toCell && !parked)
}
