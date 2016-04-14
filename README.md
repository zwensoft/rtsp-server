# rtsp-server

<h1>Publish:</h1>
<p>
ffmpeg -re  -i  The_Nut_Job_trailer.mp4  -vcodec copy -acodec copy -rtsp_transport tcp  -f rtsp rtsp://localhost:5454/live/mystream
</p>

<h1>Play:</h1>
<p>ffplay  -rtsp_transport tcp   -i rtsp://localhost:5454/live/mystream</p>