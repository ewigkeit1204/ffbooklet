/*
 * Copyright 2023 Keisuke.K <ewigkeit1204@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.ewigkeit.tool.ffbooklet;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;

/**
 * @author Keisuke.K <ewigkeit1204@gmail.com>
 */
public class Main {

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(createOptions(), args);
        } catch (ParseException e) {
            e.printStackTrace();
            System.exit(1);
        }

        String[] files = cmd.getArgs();

        if (cmd.hasOption("h") || files.length < 2 || (cmd.hasOption("l") && cmd.hasOption("r"))) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ffbooklet [options] <input.pdf> <output.pdf>", createOptions());
            System.exit(0);
        }

        try (PDDocument input = Loader.loadPDF(new File(files[0])); PDDocument output = new PDDocument()) {
            boolean isBlank = cmd.hasOption("b");
//            boolean isRhs = cmd.hasOption("r");

            PDPage front = null;
            PDPage back = null;
            PDPageTree pages = output.getPages();

            for (PDPage page : input.getPages()) {
                if (back == null) {
                    pages.add(page);
                    back = page;
                } else if (front == null) {
                    pages.insertBefore(page, back);
                    front = page;
                } else {
                    int counter = (pages.getCount() - (isBlank ? 2 : 0)) % 4;

                    if (counter == 0 || counter == 3) {
                        pages.insertBefore(page, back);
                        back = page;
                    } else {
                        pages.insertAfter(page, front);
                        front = page;
                    }
                }
            }

//            if (isRhs) {
//                for (PDPage pdPage : pages) {
//                    
//                }
//            }

            output.save(new File(files[1]));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static Options createOptions() {
        Options options = new Options();
        options.addOption(Option.builder("b").longOpt("blank").desc("back cover is blank").build());
        options.addOption(Option.builder("h").longOpt("help").desc("show usage").build());
//        options.addOption(Option.builder("l").longOpt("left-handed-side").desc("left handed side (default)").build());
//        options.addOption(Option.builder("r").longOpt("right-handed-side").desc("right handed side").build());
//        options.addOption(Option.builder().longOpt("rotate").argName("angle").hasArg()
//                .desc("rotate pages by the specified angle (angle may be 0, 90, 180, or 270").build());

        return options;
    }

}
