% %%
% v1 = VideoReader('red_VID_06_21_15_03_003.mp4');
% v2 = VideoReader('yellow_VID_06_21_15_03_003.mp4');
% 
% %%
% 
v1.CurrentTime = 0
v2.CurrentTime = 0

for i = 1:100 %225
    readFrame(v2);
end

i = 0;

skip = 100;

frame1 = readFrame(v1);
frame2 = readFrame(v2);
fc1 = 1;
fc2 = 1;
while hasFrame(v1) & hasFrame(v2)    
    subplot(2, 1, 1);
    imshow(frame1);
    subplot(2, 1, 2);
    imshow(frame2);
    c = input('save? ', 's');
    if c == 's'
        i = i+1
        % Save the frames
        matches{i}.f1 = frame1;
        matches{i}.f2 = frame2;
        matches{i}.fc = fc1;
        imwrite(frame1, sprintf('matches_yg/g/g_match_%2.2d.jpg',i));
        imwrite(frame2, sprintf('matches_yg/y/y_match_%2.2d.jpg',i));
    end
    frame1 = progress_n(v1, skip);
    fc1 = fc1 + skip;
    frame2 = progress_n(v2, skip);
    fc2 = fc2 + skip;

end

%%
%% 

% Jump eeach video to close to the end 
% v1.CurrentTime = v1.Duration - 1;
% v2.CurrentTime = v2.Duration - 1;
% v1.CurrentTime = 0;
% v2.CurrentTime = 0;
% progress_n(v1, (v1.Duration - 1) * v1.FrameRate);
% progress_n(v2, (v2.Duration - 1) * v2.FrameRate);
% 
% skip = 15;
% 
% frame1 = readFrame(v1);
% frame2 = readFrame(v2);
% fc1 = 1;
% fc2 = 1;
% while hasFrame(v1) & hasFrame(v2)    
%     subplot(2, 1, 1);
%     imshow(frame1);
%     subplot(2, 1, 2);
%     imshow(frame2);
%     c = input('top or bottom?', 's');
%     if c == 't'
%         frame1 = progress_n(v1, skip);
%         fc1 = fc1 + skip;
%     elseif c == 'b'
%         frame2 = progress_n(v2, skip);
%         fc2 = fc2 + skip;
%     end
%     fc1
%     fc2
% end

%%
% files = dir('matches_rw/r/*.jpg')
% for img_name = files'
%     img_path = sprintf('matches_rw/r/%s', img_name.name);
%     
%     im = rgb2gray(imread(img_path));
%     im_adj = imadjust(im, [0 0.1]);
%     imwrite(im_adj, sprintf('matches_rw/r_adj/%s', img_name.name));
% end
