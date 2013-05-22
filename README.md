Introduction
------------
This maven plugin invokes [JPEGTran](http://jpegclub.org/jpegtran/ "jpegtran Homepage") on a set of images. JPEGTran is a JPG optimizer which reduces the file size of images.

For sufficient performance of your build process, this plugin processes images in parallel.

This Plugin is just a fork of the maven-optipng-plugin.

Requirements
------------
It is assumed that you have `jpegtran` installed on your system and that the executable is available within your `$PATH`.

This plugin has only been tested on OS X.

Usage
-----
The following snippet demonstates a sample usage of this plugin.

```xml
		<plugin>
					        <groupId>de.holisticon</groupId>
					        <artifactId>jpegtran-maven-plugin</artifactId>
					        <version>1.0-SNAPSHOT</version>
					       	<executions>
					            <execution>
					                <goals>
					                    <goal>optimize</goal>
					                </goals>
					            </execution>
					        </executions>
					        <configuration>
					            <jpegDirectories>
					                <jpegDirectory>${basedir}/src/main/webapp/img/JustinMezzel</jpegDirectory>
					                <jpegDirectory>${basedir}/src/main/webapp/img/OliverOchs</jpegDirectory>
					            </jpegDirectories>
					        </configuration>
					</plugin>
```
