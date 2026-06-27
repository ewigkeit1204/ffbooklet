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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
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
            System.err.println("ERROR: Failed to parse command line options: " + e.getMessage());
            System.exit(1);
        }

        String[] files = cmd.getArgs();

        if (cmd.hasOption("h") || files.length < 2 || (cmd.hasOption("l") && cmd.hasOption("r"))) {
            HelpFormatter formatter = HelpFormatter.builder().get();
            try {
                formatter.printHelp("ffbooklet [options] <input.pdf> <output.pdf>", null, createOptions(), null, false);
            } catch (IOException e) {
                System.err.println("ERROR: Failed to print help: " + e.getMessage());
            }
            System.exit(0);
        }

        int degree = 0;
        if (cmd.hasOption("R")) {
            try {
                degree = Integer.parseInt(cmd.getOptionValue("R")) % 360;
                if (degree < 0) {
                    degree += 360;
                }
            } catch (NumberFormatException e) {
                System.err.println("ERROR: rotate angle must be an integer.");
                System.exit(1);
            }

            if (degree % 90 != 0) {
                System.err.println("ERROR: degree must be multiplied by 90 (0, 90, 180, 270)");
                System.exit(1);
            }
        }

        float quality = 0.8f;
        if (cmd.hasOption("q")) {
            try {
                quality = Float.parseFloat(cmd.getOptionValue("q"));
                if (quality < 0.0f || quality > 1.0f) {
                    System.err.println("ERROR: quality must be between 0.0 and 1.0");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("ERROR: quality must be a float value.");
                System.exit(1);
            }
        }

        try (PDDocument input = Loader.loadPDF(new File(files[0])); 
             PDDocument output = new PDDocument()) {

            boolean isBlank = cmd.hasOption("b");
            boolean isRhs = cmd.hasOption("r");

            List<PDPage> pageList = new ArrayList<>();
            PDPage front = null;
            PDPage back = null;

            for (PDPage page : input.getPages()) {
                BufferedImage image = getBufferedImage(page)
                        .orElseThrow(() -> new IOException("No image found on page."));
                
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

                BufferedImage[] processedImages = cropImage(image, degree);

                for (BufferedImage processed : processedImages) {
                    PDImageXObject pdImage = JPEGFactory.createFromImage(output, processed, quality);

                    PDPage newPage = new PDPage(new PDRectangle(width, height));
                    try (PDPageContentStream contentStream = new PDPageContentStream(output, newPage)) {
                        Matrix matrix = Matrix.getScaleInstance(width, height);
                        contentStream.drawImage(pdImage, matrix);
                    }
                    newPage.setRotation(degree);

                    if (back == null) {
                        pageList.add(newPage);
                        back = newPage;
                    } else if (front == null) {
                        int index = pageList.indexOf(back);
                        pageList.add(index, newPage);
                        front = newPage;
                    } else {
                        int counter = (pageList.size() - (isBlank ? 2 : 0)) % 4;

                        if (counter == 0 || counter == 3) {
                            int index = pageList.indexOf(back);
                            pageList.add(index, newPage);
                            back = newPage;
                        } else {
                            int index = pageList.indexOf(front);
                            pageList.add(index + 1, newPage);
                            front = newPage;
                        }
                    }
                }
            }

            if (isRhs) {
                Collections.reverse(pageList);
            }

            for (PDPage page : pageList) {
                output.addPage(page);
            }

            output.save(new File(files[1]));
            System.out.println("Successfully saved to: " + files[1]);

        } catch (IOException e) {
            System.err.println("ERROR: An I/O error occurred during processing.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    static BufferedImage[] cropImage(BufferedImage image, int degree) {
        BufferedImage[] images = new BufferedImage[2];
        int w = image.getWidth();
        int h = image.getHeight();

        BufferedImage sub0, sub1;
        switch (degree % 360) {
            case 0:
                sub0 = image.getSubimage(0, 0, w / 2, h);
                sub1 = image.getSubimage(w / 2, 0, w - w / 2, h);
                break;
            case 90:
                sub0 = image.getSubimage(0, h / 2, w, h - h / 2);
                sub1 = image.getSubimage(0, 0, w, h / 2);
                break;
            case 180:
                sub0 = image.getSubimage(w / 2, 0, w - w / 2, h);
                sub1 = image.getSubimage(0, 0, w / 2, h);
                break;
            case 270:
                sub0 = image.getSubimage(0, 0, w, h / 2);
                sub1 = image.getSubimage(0, h / 2, w, h - h / 2);
                break;
            default:
                throw new IllegalArgumentException("Unsupported rotation degree: " + degree);
        }

        // Subimage shares the parent image's DataBuffer, which causes PDFBox 
        // to write the entire parent image to each page. We copy them to 
        // independent BufferedImages to prevent output PDF size inflation.
        images[0] = copyImage(sub0, image.getType());
        images[1] = copyImage(sub1, image.getType());

        return images;
    }

    private static BufferedImage copyImage(BufferedImage src, int type) {
        int imageType = (type == BufferedImage.TYPE_CUSTOM) ? BufferedImage.TYPE_3BYTE_BGR : type;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        java.awt.Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    static Optional<BufferedImage> getBufferedImage(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return Optional.empty();
        }

        PDImageXObject largestImage = null;
        long maxArea = 0;

        for (COSName name : resources.getXObjectNames()) {
            if (resources.isImageXObject(name)) {
                if (resources.getXObject(name) instanceof PDImageXObject image) {
                    long area = (long) image.getWidth() * image.getHeight();
                    if (area > maxArea) {
                        maxArea = area;
                        largestImage = image;
                    }
                }
            }
        }

        if (largestImage != null) {
            return Optional.of(largestImage.getImage());
        }

        return Optional.empty();
    }

    static Options createOptions() {
        Options options = new Options();
        options.addOption(Option.builder("b").longOpt("blank").desc("back cover is blank").get());
        options.addOption(Option.builder("h").longOpt("help").desc("show usage").get());
        options.addOption(Option.builder("l").longOpt("left-handed-side").desc("left handed side (default)").get());
        options.addOption(Option.builder("r").longOpt("right-handed-side").desc("right handed side").get());
        options.addOption(Option.builder("R").longOpt("rotate").argName("angle").hasArg()
                .desc("rotate pages by the specified angle (angle may be 0, 90, 180, or 270)").get());
        options.addOption(Option.builder("q").longOpt("quality").argName("value").hasArg()
                .desc("JPEG compression quality (0.0 to 1.0, default: 0.8)").get());

        return options;
    }

}
