# rtsp-server

<h6>Publish:</h6>
<p>
ffmpeg -re  -i  The_Nut_Job_trailer.mp4  -vcodec copy -acodec copy -rtsp_transport tcp  -f rtsp rtsp://localhost:5454/live/mystream
</p>

<h6>Play:</h6>
<p>ffplay  -rtsp_transport tcp   -i rtsp://localhost:5454/live/mystream</p>