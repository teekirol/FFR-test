# TO RUN
`sbt "run readings.csv"`

# DESCRIPTION

In your favourite language*, write a library to analyse power readings from industrial assets. Your goal is to determine whether or not the device reacts quickly enough to meet National Grid requirements for Fast Frequency Response (FFR). In the FFR programme, assets that consume power automatically switch off (or "turn down") to reduce power consumption when there is an under-supply. The control signal we use is the supply frequency: when the frequency drops below a threshold, there is an under-supply of power on the grid.

OK, we can read Java, Scala, Python, Haskell, Elm, Ruby, JavaScript, Groovy and probably a few others. Don't make it too hard for us. Brainfuck is right out.
 
The National Grid's requirements are:
The trigger condition for initiating an FFR event is the supply frequency dropping below 49.7 Hz
The relay controlling the device must switch within 400ms of detecting this trigger condition
The device must shed the expected load within 30 seconds of the relay switching
The device must remain turned-down for 30 minutes
After 30 minutes, the device must start running again
 
You need to know:
The values are provided in a CSV-formatted file (see below)
The values supplied are aggregated energy readings. The numbers keep going up. The power in kilowatts is the difference between consecutive readings, divided by the time in seconds. For example, if the number changes from 100 to 101 during a period of 0.5 seconds, the power is (101 - 100)/0.5 = 2 kW.
The readings are taken from a three-phase power supply. As they are accumulated energy readings, you should sum the three values to get total power for the asset.
We run the test by injecting a test frequency profile. We don't care what happens outside that profile. The start of the profile consists of 10 frequency readings of exactly 50.00 Hz, +/- 0.01 Hz. Discard all readings before the injected profile.
The test runs for precisely 35 minutes. Discard all readings after that period.
 
CSV file format:
The data has no header, and consists of rows like:
"#2016-10-20 14:40:36:471,50.0689,0,0,0,0,1469345,1463552,1431758,0,1,off$"
Which can be read as:
"#date and timestamp,frequency,0,0,0,0,phase 1,phase 2,phase 3,0,1,relay status$"
Please ignore the non-highlighted columns - they are for unused inputs and arcane relay statuses.
 
Given the data attached below, please determine:
How long it took for the relay to switch
How long it took for the device to turn down
Whether or not the device passed the test
