# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant::Config.run do |config|
  config.vm.define :zookeeper do |zookeeper_config|
    # Every Vagrant virtual environment requires a box to build off of.
    zookeeper_config.vm.box = "ubuntu-12.10-server-64bit"

    # The url from where the 'config.vm.box' box will be fetched if it
    # doesn't already exist on the user's system.
    zookeeper_config.vm.box_url = "http://cloud-images.ubuntu.com/quantal/current/quantal-server-cloudimg-vagrant-amd64-disk1.box"

    # Forward a port from the guest to the host, which allows for outside
    # computers to access the VM, whereas host only networking does not.
    zookeeper_config.vm.forward_port 2181, 2181   # This is the port that ZooKeeper listens on

    # Enable provisioning with an inline shell script.  I'm lazy and don't want to
    # learn how to write puppet so this will serve the purpose.  Ideally in the future
    # someone smarter than me will come in and replace this to use the same puppet that
    # we use in production.  This will install a single node ZooKeeper ensemble into the VM
    # and start it up listening to port 2181 (which is also forwarded to local 2181 above).
    zookeeper_config.vm.provision :shell do |shell|
      shell.inline = <<-eos
        #!/bin/bash

        # Upgrade to the latest version of all packages
        echo Upgrading Packages
        DEBIAN_FRONTEND="noninteractive" sudo apt-get --assume-yes update
        DEBIAN_FRONTEND="noninteractive" sudo apt-get --assume-yes upgrade
        echo

        # Install zookeeper
        echo Installing ZooKeeper
        sudo add-apt-repository --yes ppa:hadoop-ubuntu/dev
        DEBIAN_FRONTEND="noninteractive" sudo apt-get --assume-yes update
        DEBIAN_FRONTEND="noninteractive" sudo apt-get --assume-yes install hadoop-zookeeper-server
        echo
      eos
    end
  end
end
