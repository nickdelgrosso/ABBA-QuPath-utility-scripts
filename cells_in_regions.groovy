import static qupath.lib.gui.scripting.QPEx.* // For intellij editor autocompletion

import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import qupath.lib.measurements.MeasurementList
import qupath.lib.objects.PathCellObject

import qupath.ext.biop.warpy.Warpy
import qupath.ext.biop.abba.AtlasTools
import net.imglib2.RealPoint

useSmallArea = false;
clearAllObjects();

// create and select rectangle (code from https://qupath.readthedocs.io/en/stable/docs/scripting/overview.html#creating-rois)

if (useSmallArea) {
    int z = 0
    int t = 0
    def plane = ImagePlane.getPlane(z, t)
    def roi = ROIs.createRectangleROI(7000, 8000, 100, 100, plane)
    def annotation = PathObjects.createAnnotationObject(roi)
    addObject(annotation)
} else {
    runPlugin('qupath.imagej.detect.tissue.SimpleTissueDetection2', '{"threshold": 1,  "requestedPixelSizeMicrons": 20.0,  "minAreaMicrons": 10000.0,  "maxHoleAreaMicrons": 1000000.0,  "darkBackground": true,  "smoothImage": false,  "medianCleanup": false,  "dilateBoundaries": false,  "smoothCoordinates": true,  "excludeOnBoundary": false,  "singleAnnotation": true}');
}

selectAnnotations();

// run Positive Cell Detection
runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', '{"detectionImage": "NeuN (Opal 690)",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 35.0,  "medianRadiusMicrons": 1.5,  "sigmaMicrons": 2.0,  "minAreaMicrons": 40.0,  "maxAreaMicrons": 600.0,  "threshold": 0.5,  "watershedPostProcess": true,  "cellExpansionMicrons": 2.0,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true,  "thresholdCompartment": "Cell: DAPI mean", "thresholdPositive1": 0.5,  "thresholdPositive2": 0.4,  "thresholdPositive3": 0.6,  "singleThreshold": true}');

// select and delete rectangle (already selected but just in case)
selectAnnotations();
clearSelectedObjects();

// load warped Allen regions
def imageData = getCurrentImageData();
def splitLeftRight = true;
qupath.ext.biop.abba.AtlasTools.loadWarpedAtlasAnnotations(getCurrentImageData(), "acronym", false);

// select all cells and insert them into hierarchy
//clearSelectedObjects();
selectCells();

def selectedObjects = getCurrentImageData().getHierarchy().getSelectionModel().getSelectedObjects();
insertObjects(selectedObjects);

// run Subcellular Spot Detection
runPlugin('qupath.imagej.detect.cells.SubcellularDetection', '{"detection[Channel 1]": -1.0,  "detection[Channel 2]": 0.4,  "detection[Channel 3]": 0.3,  "detection[Channel 4]": 0.15,  "detection[Channel 5]": 0.2,  "detection[Channel 6]": -1.0,  "detection[Channel 7]": -1.0,  "doSmoothing": false,  "splitByIntensity": true,  "splitByShape": true,  "spotSizeMicrons": 0.5,  "minSpotSizeMicrons": 0.2,  "maxSpotSizeMicrons": 7.0,  "includeClusters": false}');

def pixelToAtlasTransform = 
    qupath.ext.biop.abba.AtlasTools
    .getAtlasToPixelTransform(getCurrentImageData())
    .inverse() // pixel to atlas = inverse of atlas to pixel

getDetectionObjects().forEach(detection -> {
    RealPoint atlasCoordinates = new RealPoint(3);
    MeasurementList ml = detection.getMeasurementList();
    atlasCoordinates.setPosition([detection.getROI().getCentroidX(),detection.getROI().getCentroidY(),0] as double[]);
    pixelToAtlasTransform.apply(atlasCoordinates, atlasCoordinates);
    ml.putMeasurement("Atlas_X", atlasCoordinates.getDoublePosition(0) )
    ml.putMeasurement("Atlas_Y", atlasCoordinates.getDoublePosition(1) )
    ml.putMeasurement("Atlas_Z", atlasCoordinates.getDoublePosition(2) )
})



// change cell name to replace name with unique ID number
counter = 1

selectDetections()
detections = getSelectedObjects()

for (detection in detections) {
    if (detection.class.equals(PathCellObject.class)) {
        detection.setName(counter.toString());
        ++counter
    } else {
        detection.setName('');
    }
}


// save annotations
File directory = new File(buildFilePath(PROJECT_BASE_DIR,'export2'));
directory.mkdirs();
imageName = ServerTools.getDisplayableImageName(imageData.getServer())
saveAnnotationMeasurements(buildFilePath(directory.toString(),imageName+'__annotations.tsv'));
saveDetectionMeasurements(buildFilePath(directory.toString(),imageName+'__detections.tsv'));
