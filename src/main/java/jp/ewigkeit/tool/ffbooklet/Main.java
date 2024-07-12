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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

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
            boolean isRhs = cmd.hasOption("r");
            int degree = 0;

            if (cmd.hasOption("R")) {
                degree = Integer.parseInt(cmd.getOptionValue("rotate"));

                if (degree % 90 != 0) {
                    System.err.println("ERROR: degree must be multiplied by 90");
                    System.exit(1);
                }
            }

            PDPage front = null;
            PDPage back = null;
            PDPageTree pages = output.getPages();

            for (PDPage page : input.getPages()) {
                BufferedImage image = getBufferedImage(page).orElseThrow();
                PDRectangle mediaBox = page.getMediaBox();
                float width = 0;
                float height = 0;

                if (degree % 180 == 90) {
                    width = mediaBox.getHeight() / 2;
                    height = mediaBox.getWidth();
                } else {
                    width = mediaBox.getWidth() / 2;
                    height = mediaBox.getHeight();
                }

                for (BufferedImage processed : processImage(image, degree)) {
                    PDImageXObject pdImage = JPEGFactory.createFromImage(output, processed);
                    PDPage newPage = new PDPage(new PDRectangle(width, height));
                    try (PDPageContentStream contentStream = new PDPageContentStream(output, newPage)) {
                        Matrix matrix = Matrix.getScaleInstance(width, height);
                        contentStream.drawImage(pdImage, matrix);
                    }

                    if (back == null) {
                        pages.add(newPage);
                        back = newPage;
                    } else if (front == null) {
                        pages.insertBefore(newPage, back);
                        front = newPage;
                    } else {
                        int counter = (pages.getCount() - (isBlank ? 2 : 0)) % 4;

                        if (counter == 0 || counter == 3) {
                            pages.insertBefore(newPage, back);
                            back = newPage;
                        } else {
                            pages.insertAfter(newPage, front);
                            front = newPage;
                        }
                    }
                }

            }

            if (isRhs) {
                for (int i = pages.getCount() - 2; i >= 0; i--) {
                    PDPage page = pages.get(i);
                    pages.remove(i);
                    pages.add(page);
                }
            }

            output.save(new File(files[1]));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static BufferedImage[] processImage(BufferedImage image, int degree) {
        BufferedImage rotatedImage = rotate(image, degree);
        int chunkWidth = rotatedImage.getWidth() / 2;
        int chunkHeight = rotatedImage.getHeight();
        BufferedImage images[] = new BufferedImage[2];

        for (int x = 0; x < 2; x++) {
            images[x] = new BufferedImage(chunkWidth, chunkHeight, rotatedImage.getType());

            Graphics2D g = images[x].createGraphics();
            g.drawImage(rotatedImage, 0, 0, chunkWidth, chunkHeight, chunkWidth * x, 0, chunkWidth * (x + 1),
                    chunkHeight, null);
            g.dispose();
        }

        return images;
    }

    static BufferedImage rotate(BufferedImage image, int degree) {
        // rotate
        int width = image.getWidth();
        int height = image.getHeight();
        int x = 0;
        int y = 0;
        BufferedImage rotatedImage;

        switch (degree % 360) {
        case 0:
            return image;
        case 90:
            x = height;
            rotatedImage = new BufferedImage(height, width, image.getType());
            break;
        case 180:
            x = width;
            y = height;
            rotatedImage = new BufferedImage(width, height, image.getType());
            break;
        case 270:
            y = width;
            rotatedImage = new BufferedImage(height, width, image.getType());
            break;
        default:
            throw new IllegalArgumentException();
        }

        Graphics2D g = rotatedImage.createGraphics();
        g.translate(x, y);
        g.rotate(Math.toRadians(degree));
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return rotatedImage;
    }

    static Optional<BufferedImage> getBufferedImage(PDPage page) throws IOException {
        PDResources resources = page.getResources();

        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject image) {
                return Optional.of(image.getImage());
            }
        }

        return Optional.empty();
    }

    static Options createOptions() {
        Options options = new Options();
        options.addOption(Option.builder("b").longOpt("blank").desc("back cover is blank").build());
        options.addOption(Option.builder("h").longOpt("help").desc("show usage").build());
        options.addOption(Option.builder("l").longOpt("left-handed-side").desc("left handed side (default)").build());
        options.addOption(Option.builder("r").longOpt("right-handed-side").desc("right handed side").build());
        options.addOption(Option.builder("R").longOpt("rotate").argName("angle").hasArg()
                .desc("rotate pages by the specified angle (angle may be 0, 90, 180, or 270").build());

        return options;
    }

}
