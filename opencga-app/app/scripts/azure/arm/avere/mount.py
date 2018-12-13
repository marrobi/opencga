import sys
import socket
import fcntl
import struct
import random
import os
import shutil
import subprocess
import time

# Run `python -m unittest discover` in this dir to execute tests

default_mount_options_nfs = "nfs hard,nointr,proto=tcp,mountproto=tcp,retry=30 0 0"
default_mount_options_cifs = "dir_mode=0777,file_mode=0777,serverino,nofail,vers=3.0"


def get_avere_ips(vserver_string):
    vserver_range = vserver_string.split("-")
    if len(vserver_range) < 2:
        print("Expect vserver ip range to be in the format 'startip-endip' got:" + vserver_string)
        exit(3)

    all_ips = []
    start_ip = vserver_range[0]
    end_ip = vserver_range[1]

    start_ip_parts = start_ip.split(".")
    start_ip_last_digit = int(start_ip_parts[3])
    ip_prefix = start_ip_parts[0] + "." + start_ip_parts[1] + "." + start_ip_parts[2] + "."
    end_ip_last_digit = int(end_ip.split(".")[3])

    for ip in range(start_ip_last_digit, end_ip_last_digit + 1): 
        all_ips.append(ip_prefix + str(ip))
    
    return all_ips

def get_ip_address():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # doesn't even have to be reachable
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def ip_as_int(ip):
    o = map(int, ip.split('.'))
    res = (16777216 * o[0]) + (65536 * o[1]) + (256 * o[2]) + o[3]
    return res

def remove_lines_containing(file, contains):
    with open(file,"r+") as file:
        d = file.readlines()
        file.seek(0)
        for i in d:
            if contains not in i and i != "\n":
                file.write(i)
        file.truncate()

def print_help():
    print "For example 'sudo python mount.py avere 10.0.1.10-10.0.1.14'"
    print "or 'sudo python mount.py azurefiles <storage-account-name>,<share-name>,<storage-account-key>'"

def install_apt_package(package):
    try:
        print "Attempt to install " + package
        subprocess.check_call(["apt", "install", package, "-y"])
        print "Install completed successfully"
    except subprocess.CalledProcessError as e:
        print "Failed install " + package + " error:" + e.message
        exit(4)

def main():  
    if len(sys.argv) < 3:
        print "Expected arg1: 'mount_type' and arg2 'mount_data'"
        print_help()
        exit(1)

    mount_type = str(sys.argv[1])
    mount_data = str(sys.argv[2])

    if mount_type.lower() != "avere" and mount_type.lower() != "azurefiles": 
        print "Expected first arg to be either 'avere' or 'azurefiles'"
        print_help()
        exit(1)

    if mount_data == "":
        print "Expected second arg to be the mounting data. For avere this is the vserver iprange. Fo azure files this should be the azure files connection details."
        print_help()
        exit(2)


    print 'Mounting type:' + sys.argv[1]
    print 'Mounting data:' + sys.argv[2]
    
    mount_point_permissions = 0o0777 #Todo: What permissions does this really need?
    primary_mount_folder = "/media/primarynfs"
    seconday_mount_folder_prefix = "/media/secondarynfs"

    try:
        # Create folder to mount to 
        if not os.path.exists(primary_mount_folder):
            os.makedirs(primary_mount_folder)
            os.chmod(primary_mount_folder, mount_point_permissions)
        
        # Make a backup of the fstab config incase we go wrong
        shutil.copy("/etc/fstab", "/etc/fstab-averescriptbackup")

        # Clear existing NFS mount data to make script idempotent
        remove_lines_containing("/etc/fstab", primary_mount_folder)
        remove_lines_containing("/etc/fstab", seconday_mount_folder_prefix)

        if mount_type.lower() == "azurefiles":
            install_apt_package("cifs-utils")

            params = mount_data.split(",")
            if len(params) != 3:
                print "Wrong params for azure files mount, expected 3 as CSV"
                print_help()
                exit(1)
            
            account_name = params[0]
            share_name = params[1]
            account_key = params[2]

            with open('/etc/fstab', 'a') as file:
                print "Mounting primary"
                file.write("\n//{0}.file.core.windows.net/{1} {2} cifs username={0},password={3},{4} \n"
                    .format(account_name, share_name, primary_mount_folder, account_key, default_mount_options_cifs))
                

        if mount_type.lower() == "avere":
            install_apt_package("nfs-common")            

            ips = get_avere_ips(mount_data)
            print "Found ips:" + ",".join(ips)

            # Deterministically select a primary node from the available
            # servers for this vm to use. By using the ip as a seed this ensures
            # re-running will get the same node as primary
            current_ip = get_ip_address()
            current_ip_int = ip_as_int(current_ip)
            print "Using ip as int: {0} for random seed".format(current_ip_int)
            random.seed(current_ip_int)
            random_node = random.randint(0, len(ips))

            primary = ips[random_node]
            ips.remove(primary)
            secondarys = ips

            print "Primary node selected:" + primary
            print "Secondary nodes selected:" + ",".join(secondarys)

            with open('/etc/fstab', 'a') as file:

                print "Mounting primary"
                file.write("\n"+ primary +":/msazure "+ primary_mount_folder + " "+ default_mount_options_nfs + "\n")
                
                print "Mounting secondarys"
                number = 0
                for ip in secondarys:
                    number = number+1
                    folder = "/media/secondarynfs" + str(number)
                    if not os.path.exists(folder):
                        os.makedirs(folder)
                        os.chmod(folder, mount_point_permissions)
                    
                    file.write("\n"+ ip +":/msazure "+ folder + " "+ default_mount_options_nfs + "\n")
    except IOError as (errno, strerror):
        print "I/O error({0}): {1}".format(errno, strerror)
        exit(1)
    except:
        print "Unexpected error:", sys.exc_info()[0]
        exit(1)

    print "Done editing fstab ... attempting mount"

    # Retry mounting for a while to handle race where VM exists before storage 
    # or temporary issue with storage
    retryExponentialFactor = 3
    for i in range(1,100):
        if i == 100:
            print "Failed to mount after max 100 retries"
            exit(3)
        try:
            print "Attempt #" + str(i)
            subprocess.check_call(["mount", "-a"])
        except subprocess.CalledProcessError as e:
            print "Failed to mount:" + e.message
            retry_in = i*retryExponentialFactor
            print "retrying in {0}secs".format(retry_in)
            time.sleep(retry_in)
            continue
        else:
            break

if __name__== "__main__":
  main()
