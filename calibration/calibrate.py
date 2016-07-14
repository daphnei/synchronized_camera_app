from skvideo.io import VideoCapture

cap = VideoCapture(filename)
cap.open()

while True:
    retval, image = cap.read()
    # image is a numpy array containing the next frame
    # do something with image here
    if not retval:
        break