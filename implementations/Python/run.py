import numpy as np
def plotHist(tableDict, value_range=None, xy_range=None, pixel_size=20, sigma=None, target_size=None):
    x = tableDict['x'][:]
    y = tableDict['y'][:]
    if xy_range:
        xmin, xmax = xy_range[0]
        ymin, ymax = xy_range[1]
    else:
        xmin, xmax, ymin, ymax = x.min(), x.max(), y.min(), y.max()
    xedges = np.arange(xmin, xmax, pixel_size)
    yedges = np.arange(ymin, ymax, pixel_size)
    H, xedgesO, yedgesO = np.histogram2d(y, x, bins=(yedges, xedges))
    if target_size is not None:
        if H.shape[0] < target_size[0] or H.shape[1] < target_size[1]:
            H = np.pad(H, ((0, target_size[0] - H.shape[0]), (0, target_size[
                       1] - H.shape[1])), mode='constant', constant_values=0)

    if value_range:
        H = H.clip(value_range[0], value_range[1])
    if sigma:
        import scipy
        H = scipy.ndimage.filters.gaussian_filter(H, sigma=(sigma, sigma))
    return H


if __name__ == '__main__':
    import time
    import matplotlib.pyplot as plt
    import smlm_file
    # Download a file from: https://shareloc.xyz/#/repository by clicking the `...` button of the file and click the name contains `.smlm`
    # Experiment: 7GB csv file --> 21MB smlm file; loading time: 21s with python-numpy implemetation, 116s with pure python implementation.
    start_time = time.time()
    manifest, files = smlm_file.readSmlmFile('../../data/test_localization_table.smlm')
    print("--- file loaded in %s seconds ---" % (time.time() - start_time))
    h = plotHist(files[0]['data']['tableDict'], value_range=(0,10))
    plt.figure(figsize=(20,20))
    plt.imshow(h)
    plt.savefig('../../data/test_localization_histogram.png')
    # plt.figure(figsize=(20,20))
    # plt.imshow(np.array(files[1]['data']['image']))
    print("--- histogram plotted in %s seconds ---" % (time.time() - start_time))
