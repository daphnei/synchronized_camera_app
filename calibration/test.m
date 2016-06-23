im_c1 = imread('camera1/02.jpg');
im_c2 = imread('camera2/02.jpg');

figure();
imshow(im_c1)
hold on
plot(imagePoints(:,1,1,1), imagePoints(:,2,1,1), 'ro')

x1 = [imagePoints(1,:,1,1) 1]
X1 = (stereoParams.CameraParameters1.IntrinsicMatrix ^ -1) * x1'

X2 = stereoParams.RotationOfCamera2  * (X1- stereoParams.TranslationOfCamera2');

% AA = x1 * stereoParams.FundamentalMatrix;
% cvx reset
% cvx begin
% variable x2(3)
% minimize norm(AA * transpose(x2))
% subject to 
% 	AA * transpose(x2) == 0
% 	norm(x2) == 1
% cvx end
% 
% 
