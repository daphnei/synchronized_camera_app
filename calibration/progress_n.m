function [frame] = progress_n( video, n )
%PROGRESS_N Summary of this function goes here
%   Detailed explanation goes here

for i = 1:n
    frame = readFrame(video);
end

end

