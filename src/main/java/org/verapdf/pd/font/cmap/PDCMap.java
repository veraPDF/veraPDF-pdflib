package org.verapdf.pd.font.cmap;

import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASFileInStream;
import org.verapdf.as.io.ASInputStream;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSStream;
import org.verapdf.tools.IntReference;

import java.io.*;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents CMap on PD layer.
 *
 * @author Sergey Shemyakov
 */
public class PDCMap {

    private static final Logger LOGGER = Logger.getLogger(PDCMap.class.getCanonicalName());

    private COSObject cMap;
    private COSDictionary cidSystemInfo;
    private CMap cMapFile = null;
    private boolean parsedCMap = false;

    /**
     * Constructor from COSObject.
     *
     * @param cMap is COSStream containing CMap or COSName containing name of
     *             predefined CMap.
     */
    public PDCMap(COSObject cMap) {
        this.cMap = cMap == null ? COSObject.getEmpty() : cMap;
    }

    /**
     * @return name of this CMap.
     */
    public String getCMapName() {
        if (this.cMap.getType() == COSObjType.COS_NAME) {
            return cMap.getString();
        }
        if (this.cMap.getType() == COSObjType.COS_STREAM) {
            COSObject cMapName = this.cMap.getKey(ASAtom.CMAPNAME);
            if (cMapName != COSObject.getEmpty()) {
                return cMapName.getString();
            }
        }
        return "";
    }

    /**
     * @return COSObject, representing this CMap.
     */
    public COSObject getcMap() {
        return cMap;
    }

    /**
     * @return CMap file object read from stream or loaded from predefined CMap
     * file or null if load failed.
     */
    public CMap getCMapFile() {
        if (!parsedCMap) {
            parsedCMap = true;
            if (this.cMap.getType() == COSObjType.COS_STREAM) {
                this.cMapFile = CMapFactory.getCMap(getCMapName(),
                        this.cMap.getData(COSStream.FilterFlags.DECODE));
                return this.cMapFile;
            } else if (this.cMap.getType() == COSObjType.COS_NAME) {
                String name = this.cMap.getString();
                String cMapPath = "/font/cmap/" + name;
                this.cMapFile = CMapFactory.getCMap(getCMapName(), loadCMap(cMapPath));
                return this.cMapFile;
            } else {
                return null;
            }
        }
        return this.cMapFile;
    }

    /**
     * @return Registry value from CMap CIDSystemInfo dictionary.
     */
    public String getRegistry() {
        if (this.getCIDSystemInfo() == null) {
            return null;
        }
        return this.getCIDSystemInfo().getStringKey(ASAtom.REGISTRY);
    }

    /**
     * @return Ordering value from CMap CIDSystemInfo dictionary.
     */
    public String getOrdering() {
        if (this.getCIDSystemInfo() == null) {
            return null;
        }
        return this.getCIDSystemInfo().getStringKey(ASAtom.ORDERING);
    }

    /**
     * @return Supplement value from CMap CIDSystemInfo dictionary.
     */
    public Long getSupplement() {
        if (this.getCIDSystemInfo() == null) {
            return null;
        }
        return this.getCIDSystemInfo().getIntegerKey(ASAtom.SUPPLEMENT);
    }

    public COSObject getUseCMap() {
        COSObject res = this.cMap.getKey(ASAtom.USE_CMAP);
        return res == null ? COSObject.getEmpty() : res;
    }

    private COSDictionary getCIDSystemInfo() {
        if (this.cMap.getType() == COSObjType.COS_NAME) {
            // actually creating COSDictionary with values from predefined CMap.
            String registry = this.getCMapFile().getRegistry();
            String ordering = this.getCMapFile().getOrdering();
            int supplement = this.getCMapFile().getSupplement();
            COSDictionary res = (COSDictionary)
                    COSDictionary.construct(ASAtom.REGISTRY, registry).get();
            res.setStringKey(ASAtom.ORDERING, ordering);
            res.setIntegerKey(ASAtom.SUPPLEMENT, supplement);
            return res;
        }

        if (cidSystemInfo == null) {
            this.cidSystemInfo = (COSDictionary)
                    this.cMap.getKey(ASAtom.CID_SYSTEM_INFO).getDirectBase();
            return this.cidSystemInfo;
        }
        return this.cidSystemInfo;
    }

    private static ASInputStream loadCMap(String cMapName) {
        try {
            File cMapFile;
            URL res = PDCMap.class.getResource(cMapName);
            if (res == null) {
                throw new IOException("CMap " + cMapName + " can't be found.");
            }
            if (res.toString().startsWith("jar:")) {
                cMapFile = File.createTempFile("tempfile", ".tmp");
                InputStream input = PDCMap.class.getResourceAsStream(cMapName);
                OutputStream out = new FileOutputStream(cMapFile);
                int read;
                byte[] bytes = new byte[1024];

                while ((read = input.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
                }
                input.close();
                out.close();
                cMapFile.deleteOnExit();
            } else {
                cMapFile = new File(res.getFile());
            }
            if (!cMapFile.exists()) {
                throw new IOException("Error: File " + cMapFile + " not found!");
            }
            return new ASFileInStream(
                    new RandomAccessFile(cMapFile, "r"), 0, cMapFile.length(),
                    new IntReference(), cMapFile.getAbsolutePath(), false);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Error in opening predefined CMap " + cMapName, e);
            return null;
        }
    }

    public String toUnicode(int code) {
        return this.getCMapFile() == null ? null : this.getCMapFile().getUnicode(code);
    }
}
