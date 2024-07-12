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
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
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
                degree = Integer.parseInt(cmd.getOptionValue("rotate")) % 360;

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
                    width = mediaBox.getWidth();
                    height = mediaBox.getHeight() / 2;
                } else {
                    width = mediaBox.getWidth() / 2;
                    height = mediaBox.getHeight();
                }

                for (BufferedImage processed : cropImage(image, degree)) {
                    PDImageXObject pdImage = JPEGFactory.createFromImage(output, processed, 0.9f);
                    PDPage newPage = new PDPage(new PDRectangle(width, height));
                    try (PDPageContentStream contentStream = new PDPageContentStream(output, newPage)) {
                        Matrix matrix = Matrix.getScaleInstance(width, height);
                        contentStream.drawImage(pdImage, matrix);
                    }
                    newPage.setRotation(degree);

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

    static BufferedImage[] cropImage(BufferedImage image, int degree) {
        CropImageFilter[] filters = new CropImageFilter[2];
        BufferedImage[] images = new BufferedImage[filters.length];

        switch (degree % 360) {
        case 0:
            filters[0] = new CropImageFilter(0, 0, image.getWidth() / 2, image.getHeight());
            filters[1] = new CropImageFilter(image.getWidth() / 2, 0, image.getWidth() / 2, image.getHeight());
            break;
        case 90:
            filters[0] = new CropImageFilter(0, image.getHeight() / 2, image.getWidth(), image.getHeight() / 2);
            filters[1] = new CropImageFilter(0, 0, image.getWidth(), image.getHeight() / 2);
            break;
        case 180:
            filters[0] = new CropImageFilter(image.getWidth() / 2, 0, image.getWidth() / 2, image.getHeight());
            filters[1] = new CropImageFilter(0, 0, image.getWidth() / 2, image.getHeight());
            break;
        case 270:
            filters[0] = new CropImageFilter(0, 0, image.getWidth(), image.getHeight() / 2);
            filters[1] = new CropImageFilter(0, image.getHeight() / 2, image.getWidth(), image.getHeight() / 2);
            break;
        default:
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < filters.length; i++) {
            Image processedImage = Toolkit.getDefaultToolkit()
                    .createImage(new FilteredImageSource(image.getSource(), filters[i]));

            if (processedImage instanceof BufferedImage bufferedImage) {
                images[i] = bufferedImage;
            } else {
                images[i] = new BufferedImage(processedImage.getWidth(null), processedImage.getHeight(null),
                        image.getType());

                Graphics2D g = images[i].createGraphics();
                g.drawImage(processedImage, 0, 0, null);
                g.dispose();
            }
        }

        return images;
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
