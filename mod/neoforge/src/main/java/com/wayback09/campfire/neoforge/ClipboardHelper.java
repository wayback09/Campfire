package com.wayback09.campfire.neoforge;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/**
 * Utility for copying screenshot images directly to the OS clipboard.
 */
public final class ClipboardHelper {
    private ClipboardHelper() {}

    public static CompletableFuture<Boolean> copyImage(Path imagePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(imagePath.toFile());
                if (img == null) return false;
                TransferableImage transferable = new TransferableImage(img);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
                return true;
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
        });
    }

    private static class TransferableImage implements Transferable {
        private final Image image;

        public TransferableImage(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
