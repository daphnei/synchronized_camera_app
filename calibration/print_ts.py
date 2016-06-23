#!/usr/bin/python

from subprocess import Popen, PIPE
import re
import argparse
import datetime
import time
import math

def read_timestamps(probe, fname, nprint):
	debug = False
	if debug:
		p = Popen(probe + " -v warning -select_streams v -show_streams " +
				  " " + fname, shell=True,
				  stdout = PIPE, bufsize = 1)
#		p = Popen(probe + " -v warning -select_streams v -show_packets " +
#				  " " + fname, shell=True,
#				  stdout = PIPE, bufsize = 1)
	else:
		p = Popen(probe + " -v warning -select_streams v -show_entries " +
				  "packet=pts_time,dts_time,flags " + fname, shell=True,
				  stdout = PIPE, bufsize = 1)

	tsd = {}
	pts = 0
	dts = -1
	np  = 0
	kf  = 0
	with p.stdout:
		for line in iter(p.stdout.readline, b''):
			if debug:
				print line,
				continue
#			print line,
			s = re.split('=', line.rstrip())

			m = re.search('pts_time=', line)
			if m:
				try:
					pts = float(s[1])
				except ValueError:
					pts = -1
			m = re.search('dts_time=', line)
			if m:
				try:
					dts = float(s[1])
				except ValueError:
					dts = -1

			m = re.search('flags=', line)
			if m:
				kf = "I" if s[1] == 'K' else 'P'
				tsd[pts] = kf
				print "pts: {0:5f} dts: {1:5f} key: {2}".format(pts, dts, kf)
				if nprint and np > int(nprint):
					break
				np = np + 1

	p.wait()
	v = tsd.values()
	print '{} frame count: P: {}, I:{}, B:{}, total: {}'.format(
		fname, v.count('P'), v.count('I'), v.count('B'), len(tsd), fname)
	return tsd


if __name__ == "__main__":
	parser = argparse.ArgumentParser(
		description='prints timestamps of video')
	parser.add_argument('-i', metavar='filename',
						help='name of file to split', required=True)
	parser.add_argument('-n', metavar='num_timestamps',
						help='number of timestamps to print')
	args = vars(parser.parse_args())
	ts  = read_timestamps("ffprobe", args['i'], args['n'])
#	for i in sorted(ts.keys()):
#		print "{} {}".format(i, ts[i])
