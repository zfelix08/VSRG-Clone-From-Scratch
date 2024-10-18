// VERTICAL SCROLLING RHYTHM GAME CLONE (by: Felix Zheng)
// Styled like Guitar Hero, DDR, osu!mania, stepmania, etc.

// This is a four key vertical scrolling rhythm game with a built in timing feature and score system
// Notes are randomly generated and will be different each time
// Controls are: D, F, J, K
// The game will run for 16 beats and then give you a score

// WARNING: THIS GAME MIGHT BE A BIT HARD

// Submitted on: October 17



// CHANGELOG!!!

/* Version 1.0
 * Basic mapping system with QueueNote() Function
 * Limited-feature random note generation model
 * Note timing judgement system (perfect, great, good, miss)
 * Simple score and accuracy system
 */

package script;

import java.awt.*;
import javax.imageio.*;
import java.io.*;
import hsa2.GraphicsConsole;
import java.util.*;
import java.time.*;

public class GameScript {
	// Initialize game setting variables
	static int offset = -70;
	static char[] keyBinds = {'D', 'F', 'J', 'K'};
	
	// Initialize keypress-monitoring variables
	static boolean[] keyStates = new boolean[4];
	static boolean[] prevKeyStates = {false, false, false, false};
	
	// Initialize external library variables
	static Clock clock = Clock.systemDefaultZone();
	static GraphicsConsole g = new GraphicsConsole(1000, 1000, 50);
	
	// Initialize game speed variables
	static double fps = 300;
	static long prevMillis = 0;
	
	// Initialize variables to store note data
	static ArrayList<double[]> notes = new ArrayList<double[]>();
	static ArrayList<double[]> queuedNotes = new ArrayList<double[]>();
	static double bpm = 100;
	static double beatLength = 60000 / bpm;
	static double sixteenthNoteLength = beatLength / 4;
	
	// Initialize time-tracking variables
	static long runTime;
	static long startTime;
	
	// Initialize note judgement timing constants and variables
	static final int hitPos = 820;
	static final double perfectTiming = 36;
	static final double greatTiming = 72;
	static final double goodTiming = 144;
	static final double missTiming = 288;
	static int[] keyTimings = {0, 0, 0, 0};
	static long[] lastTimingIndex = {-1000, -1000, -1000, -1000};
	static int[] totalTimingHits = {0, 0, 0, 0};
	
	// Initialize gameplay variables
	static double score = 0;
	static double maxScore = 0;
	static double accuracy;
	
	// Initialize game scene variables ("loading" is set to true because it occurs first)
	static boolean loading = true;
	static boolean game = false;
	static boolean results = false;
	
	// Main function which runs at startup
	public static void main(String[] args) throws IOException, InterruptedException
	{
		// Initialize timing judgment image resources
		Image perfectImg = ImageIO.read(new File("src/img/perfect.png"));
		Image greatImg = ImageIO.read(new File("src/img/great.png"));
		Image goodImg = ImageIO.read(new File("src/img/good.png"));
		Image missImg = ImageIO.read(new File("src/img/miss.png"));
		Image[] judgementImgs = {missImg, goodImg, greatImg, perfectImg};
		
		// Initialize draw settings
		g.setBackgroundColor(Color.black);
		g.setStroke(15);
		g.setFont(new Font("Tahoma", Font.BOLD, 18));
		g.setColor(Color.white);
		g.clear();
		
		// Loops as the game is running
		while (true)
		{
			// Runs as the game is loading
			while (loading)
			{
				// Initialize/Reset time
				startTime = clock.millis();
				
				// Generate 16 beats worth of notes
				GenerateNotes(16);
				
				// Loading is done, so the game transitions to the playable phase
				loading = false;
				game = true;
			}
			
			// Runs as the game is functioning
			while (game)
			{
				// Update runtime and key states
				runTime = clock.millis() - startTime;
				UpdateKeyStates();
				
				// Everything in this if statement is called every frame
				if (clock.millis() - prevMillis >= 1000 / fps)
				{	
					prevMillis = clock.millis();
					
					synchronized (g)
					{	
						g.clear();
					
						// Checks for keybind presses and changes the appearance of the corresponding note receptors as fit
						for (int i = 1; i <= 4; i++)
						{
							if (keyStates[i - 1])
							{
								g.fillOval(i * 140 + 100, hitPos, 100, 100);
							}
							else
							{
								g.drawOval(i * 140 + 100, hitPos, 100, 100);
							}	
						}
						
						// Checks to see if queued notes should be spawned and spawns them
						AddNotes();
						
						// Updates all notes currently displayed on screen and checks if notes are hit/missed
						UpdateNotes();
						
						// Updates the player's current score
						DrawScoreAccuracy();
						
						// Displays the judgement of notes that are hit or missed, if applicable
						UpdateTimings(judgementImgs);
						
						// Checks if all the notes have been pressed/missed and then moves the game onto the results phase
						CheckSongEnd();
					}
				}
			}
			
			// Runs as the game displays the player's most recent game performance
			while (results)
			{
				g.clear();
				
				// Prints the player's final accuracy and score
				g.setFont(new Font("Tahoma", Font.BOLD, 70));
				g.drawString("Score: " + Math.round(score), 200, 250);
				g.drawString("Accuracy: " + accuracy + "%", 200, 350);
				
				// Draws the images of all note timing judgements
				g.drawImage(perfectImg, 300, 500, 200, 33);
				g.drawImage(greatImg, 300, 550, 200, 33);
				g.drawImage(goodImg, 300, 600, 200, 33);
				g.drawImage(missImg, 300, 650, 200, 33);
				
				// Prints the number of each timing judgement the player received
				g.setFont(new Font("Tahoma", Font.BOLD, 25));
				g.drawString(Integer.toString(totalTimingHits[3]), 550, 525);
				g.drawString(Integer.toString(totalTimingHits[2]), 550, 575);
				g.drawString(Integer.toString(totalTimingHits[1]), 550, 625);
				g.drawString(Integer.toString(totalTimingHits[0]), 550, 675);
				
				//Prints the play again message
				g.drawString("Press 'R' to play again!", 300, 850);
				
				// Exits out of the results phase until the player presses "R"
				results = false;
			}
			
			// Checks if the player presses "R"
			Restart();
		}
	}
	
	// Checks if the keybinds are pressed and stores their states in the "keyStates" array
	public static void UpdateKeyStates()
	{
		for (int i = 0; i < keyStates.length; i++)
		{
			keyStates[i] = g.isKeyDown(keyBinds[i]);
		}
	}
	
	// Adds a note to the "queuedNotes" arraylist
	// "lane" overload can range from 1-4, which determines which lane the note appears in. Set as a double instead of an int to avoid conversion frustrations
	// "speed" overload determines how much a currently displayed note is translated downwards every frame
	// "beat" and "sixteenthNote" overloads determine when a queued note should be spawned with musical intervals. beat = 1/4 note, sixteenthNote = 1/16 note
	public static void QueueNote(double lane, double speed, double beat, double sixteenthNote)
	{
		double notePos = beat * beatLength + sixteenthNote * sixteenthNoteLength + 500;
		double[] noteData = {lane, speed, 0, notePos};
		queuedNotes.add(noteData);
	}
	
	// Checks all entries in the "queuedNotes" arraylist and moves it to the "notes" arraylist if it is time to spawn it
	public static void AddNotes()
	{
		for (int i = 0; i < queuedNotes.size(); i++)
		{
			if (runTime >= queuedNotes.get(i)[3])
			{
				notes.add(queuedNotes.get(i));
				queuedNotes.remove(i);
			}
		}	
	}
	
	// Updates all notes in the "notes" arraylist either by spawning them in, translating them down (to achieve a scrolling effect), or deleting them after they go offscreen
	public static void UpdateNotes()
	{
		for (int i = 0; i < notes.size(); i++)
		{	
			// Define note properties
			int xPos = (int) notes.get(i)[0] * 140 + 100;
			int speed = (int) notes.get(i)[1];
			int yPos = (int) notes.get(i)[2];
			int laneIndex = (int) notes.get(i)[0] - 1;
			
			// Adjust note y-position based on the value of the "offset" setting
			int offsetYPos = yPos + offset;
			
			// Monitors for keypresses if the note is still onscreen
			if (offsetYPos <= 1050)
			{
				notes.get(i)[2] += speed;
				g.fillOval(xPos, yPos, 100, 100);
				
				// Checks if the corresponding key to the note's lane is pressed
				if (prevKeyStates[laneIndex] != keyStates[laneIndex])
				{
					// ONLY detects the inital pressing action to prevent a bug in which holding a key will automatically press notes
					if (keyStates[laneIndex])
					{
						prevKeyStates[laneIndex] = true;
						
						// Checks the distance of the note to the note receptor when the corresponding key is pressed
						// Assigns a perfect, great, or good judgment based on how far the note is from the receptor.
						if (offsetYPos > hitPos - perfectTiming && offsetYPos < hitPos + perfectTiming)  // Perfect
						{
							keyTimings[laneIndex] = 3;
							lastTimingIndex[laneIndex] = runTime;
							
							// Prints the judgement and the hit error for each note, for debug purposes
							System.out.println("perfect " + (offsetYPos - hitPos));
							
							score += 4;
							maxScore += 4;
							totalTimingHits[3]++;
							
							notes.remove(i);
							i--;
						}
						else if (offsetYPos > hitPos - greatTiming && offsetYPos < hitPos + greatTiming)  // Great
						{
							keyTimings[laneIndex] = 2;
							lastTimingIndex[laneIndex] = runTime;
							
							// Prints the judgement and the hit error for each note, for debug purposes
							System.out.println("great " + (offsetYPos - hitPos));
							
							score += 2;
							maxScore += 4;
							totalTimingHits[2]++;
							
							notes.remove(i);
							i--;
						}
						else if (offsetYPos > hitPos - goodTiming && offsetYPos < hitPos + goodTiming)  // Good
						{
							keyTimings[laneIndex] = 1;
							lastTimingIndex[laneIndex] = runTime;
							
							// Prints the judgement and the hit error for each note, for debug purposes
							System.out.println("good " + (offsetYPos - hitPos));
							
							score += 1;
							maxScore += 4;
							totalTimingHits[1]++;
							
							notes.remove(i);
							i--;
						}
						else if (offsetYPos > hitPos - missTiming && offsetYPos < hitPos + missTiming)  // Miss
						{
							keyTimings[laneIndex] = 0;
							lastTimingIndex[laneIndex] = runTime;
							
							// Prints the judgement and the hit error for each note, for debug purposes
							System.out.println("miss " + (offsetYPos - hitPos));
							
							maxScore += 4;
							totalTimingHits[0]++;
							
							notes.remove(i);
							i--;
						}
					}
					else
					{
						// Sets the previous state of the key to false immediately after it is released. Helps prevent the key holding bug mentioned above
						prevKeyStates[laneIndex] = false;
					}
				}
			}
			else
			{	
				// Judges a note as "miss" if the note goes offscreen before getting hit
				keyTimings[laneIndex] = 0;
				lastTimingIndex[laneIndex] = runTime;
				
				// Prints the judgement and the hit error for each note, for debug purposes
				System.out.println("miss not hit");
				
				maxScore += 4;
				totalTimingHits[0]++;
				
				notes.remove(i);
				i--;
			}
		}
	}
	
	// Checks the "keyTimings" array and passes the data off to the DrawTiming() function
	// Overload "judgementResources" is an array of 4 images that contains the miss, good, great, and perfect judgement images
	public static void UpdateTimings(Image[] judgementResources) throws IOException
	{
		for (int i = 0; i < keyTimings.length; i++)
		{
			// Makes sure only one timing gets displayed per lane at a time, and that a timing lasts for 500 ms if not overwritten by another timing
			if (runTime - lastTimingIndex[i] <= 500)
			{
				// X-position to draw the judgement image to
				int xPos = i * 140 + 208;
				
				// Index of the judgement image in the array. 0 = miss, 1 = good, 2 = great, 3 = perfect
				int judgementValue = keyTimings[i];
				
				switch (judgementValue)
				{
				case 0: // Miss
					g.drawImage(judgementResources[judgementValue], xPos, 760, 160, 25);
					break;
					
				case 1: // Good
					g.drawImage(judgementResources[judgementValue], xPos, 760, 160, 25);
					break;
					
				case 2: // Great
					g.drawImage(judgementResources[judgementValue], xPos, 760, 160, 25);
					break;
					
				case 3: // Perfect
					g.drawImage(judgementResources[judgementValue], xPos, 760, 160, 25);
					break;
				}
			}
		}
	}
	
	// Prints the results (accuracy) of the play when both the "queuedNotes" and "notes" arraylists are empty (all the notes have been spawned and judged)
	public static void CheckSongEnd()
	{
		if (queuedNotes.size() == 0 && notes.size() == 0)
		{
			game = false;
			results = true;
		}
	}
	
	// Draws the player's current score and accuracy to the screen
	public static void DrawScoreAccuracy()
	{
		accuracy = Math.round(score / maxScore * 10000.0) / 100.0; 
		
		g.drawString("Score: " + score, 790, 20);
		g.drawString("Accuracy: " + accuracy + "%", 790, 50);
	}
	
	// Randomly generates notes for a set amount of beats
	// Overload "beats" describes the number of beats
	public static void GenerateNotes(int beats)
	{
		for (int i = 0; i < beats; i++)
		{
			for (int j = 1; j < 5; j++)
			{
				double lane = Math.round(Math.random()*3) + 1;
				QueueNote(lane, 6, i, j);

				// 20% chance to generate two keys to be hit at the same time 
				if (Math.random() > 0.8)
				{
					lane += Math.round(Math.random()*2 + 1);
					
					if (lane > 4)
					{
						lane -= 4;
					}
					
					QueueNote(6 % Math.round(Math.random()*3+1) + 1, 6, i, j);
				}
			}
		}
	}
	
	// Restarts the game from the beginning upon the press of "R", resetting all values that would affect the replay
	public static void Restart()
	{
		if (g.isKeyDown('R'))
		{
			// Reset Timing Variables
			for (int i = 0; i < 4; i++)
			{
				keyTimings[i] = 0;
				lastTimingIndex[i] = -1000;
				totalTimingHits[i] = 0;
			}
			
			// Reset gameplay variables
			score = 0;
			maxScore = 0;
			
			results = false;
			loading = true;
		}
	}
}
