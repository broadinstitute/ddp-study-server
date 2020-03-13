#!/usr/bin/ruby -w
# Docker Image used to call out to generate config files
$dsde_toolbox_image_name = "broadinstitute/dsde-toolbox:consul-template-20-0.0.1"

# If USE_DOCKER evaluates to false, script will try to call consul-template directly
# instead of using Docker image built in scripts
$use_docker = ENV.fetch("USE_DOCKER", "true") == "true"

$version = ENV.fetch("VERSION") { |_|
  puts "VERSION not set"
  exit 1
}

$env = ENV.fetch("ENVIRONMENT") { |_|
  puts "ENVIRONMENT var not set"
  exit 1
}



$study_guid = ENV.fetch("STUDY_GUID","")
$study_key = ENV.fetch("STUDY_KEY","")
$image_base = ENV.fetch("IMAGE_NAME","")

$nginxProxiedHost=ENV["NGINX_PROXIED_HOST"] || "backend"
puts "nginx will proxy to #{$nginxProxiedHost}"

$docsProxiedHost=ENV["DOCS_PROXIED_HOST"] || "documentation"
puts "nginx will proxy docs to #{$docsProxiedHost}"

$noSyslog = ENV.fetch("NO_SYSLOG", false)
$image = $version + "_" + $env
$manifest = ENV.fetch("MANIFEST", "manifest.rb")

$debug_flag = ENV.fetch("DEBUG", true)
$build_containers_flag = ENV.fetch("BUILD_CONTAINERS", true)
$dir = ENV.fetch("DIR", "/app")

output_dir = ENV.fetch("OUTPUT_DIR") { |_|
  puts "OUTPUT_DIR not set."
  exit 1
}

$input_dir = ENV.fetch("INPUT_DIR") { |_|
  puts "INPUT_DIR not set."
  exit 1
}

$input_dir = $input_dir ? File.absolute_path($input_dir) : nil

# If specified, the existing configuration directory will be silently overwritten upon successful
# completion.
$suppress_prompt = ARGV[0] == "-y"

# For catching errors
$failure_rendering = false
$failed_to_render_file_names = Array.new


require "base64"
require "fileutils"
require "json"
require "open3"
require "tmpdir"

$vault_token = ENV["VAULT_TOKEN"]
if $vault_token.nil?
  begin
    $vault_token = File.read("#{ENV['HOME']}/.vault-token")
  rescue StandardError
    nil
  end
end
if $vault_token.nil?
  STDERR.puts "Could not find vault token. Tried VAULT_TOKEN environment variable and " +
                  "#{ENV['HOME']}/.vault-token"
  exit 1
end
$vault_token = $vault_token.chomp

$vault_url_root = "https://clotho.broadinstitute.org:8200/v1"

# path is full path from working directory
def copy_file_from_path(path, output_file_name = nil, silent = false)
  if output_file_name.nil?
    output_file_name = File.basename(path)
  end
  if not silent
    puts "#{path} > #{output_file_name}"
  end
  FileUtils.cp("#{$input_dir}/#{path}", "#{output_file_name}")
end

def copy_file(file_name, output_file_name = nil, custom_path = nil)
  if custom_path
    path = "#{custom_path}/#{file_name}"
  else
    path = "#{file_name}"
  end
  copy_file_from_path(path, output_file_name)
end

def set_vault_token
  if $vault_token.nil?
    $vault_token = ENV["VAULT_TOKEN"]
  end
  if $vault_token.nil?
    begin
      $vault_token = File.read("#{ENV['HOME']}/.vault-token")
    rescue StandardError
      nil
    end
  end
  if $vault_token.nil?
    STDERR.puts "Could not find vault token. Tried VAULT_TOKEN environment variable and " +
                    "#{ENV['HOME']}/.vault-token"
    exit 1
  end
  $vault_token = $vault_token.chomp
end

def read_secret_from_path(path, field = nil)
  if field.nil?
    field = "value"
  end

  # Not sure why Vault requires the -1 flag, but it does.
  curl_cmd = ["curl","-1", "-H", "X-Vault-Token: #{$vault_token}", "#{$vault_url_root}/#{path}"]
  Open3.popen3(*curl_cmd) { |stdin, stdout, stderr, wait_thread|
    if wait_thread.value.success?
      json = JSON.load(stdout)
      data = json["data"]
      if data.nil?
        STDERR.puts "Could not find secret at path: #{path}"
        STDERR.puts JSON.pretty_generate(json)
        exit 1
      end
      value = data[field]
      if value.nil?
        STDERR.puts "Could not find field '#{field}' in vault data:"
        STDERR.puts JSON.pretty_generate(data)
        exit 1
      end
      value
    else
      STDERR.puts "Curl command failed:"
      STDERR.puts stderr.read
      exit 1
    end
  }
end

def read_secret(file_name, field = nil)
  read_secret_from_path("secret/pepper/#{$env}/#{$version}/#{file_name}", field)
end

def copy_secret_from_path(path, field = nil, output_file_name = nil, silent = false)
  if output_file_name.nil?
    output_file_name = File.basename(path)
  end
  IO.write(output_file_name, read_secret_from_path(path, field))
  if not silent
    puts "#{path} > #{output_file_name}"
  end
end

def copy_secret(file_name, field = nil, output_file_name = nil)
  copy_secret_from_path("secret/pepper/#{$env}/#{$version}/#{file_name}", field, output_file_name)
end

def render_from_path(path, output_file_name = nil)
  file_name = File.basename(path)
  if output_file_name.nil?
    base, ext, _ = file_name.split(".")
    if _.nil?
      output_file_name = "#{base}"
    else
      output_file_name = "#{base}.#{ext}"
    end
  end
  copy_file_from_path(path)
  if $use_docker == true
    vault_cmd = [
             "docker", "run", "--rm", "-w", "/w", "-v", "#{Dir.pwd}:/w",
                  "-e", "VAULT_TOKEN=#{$vault_token}", "-e", "ENVIRONMENT=#{$env}", "-e", "VERSION=#{$version}",
                  "-e", "BUILD_CONTAINERS=#{$build_containers_flag}",
                  "-e", "DOCS_PROXIED_HOST=#{$docsProxiedHost}",
                  "-e", "NGINX_PROXIED_HOST=#{$nginxProxiedHost}",
                  "-e", "NO_SYSLOG=#{$noSyslog}",
                  "-e", "DEBUG=#{$debug_flag}",
                  "-e", "DIR=#{$dir}", "-e", "IMAGE=#{$image}",
                  "-e", "STUDY_GUID=#{$study_guid}",
                  "-e", "STUDY_KEY=#{$study_key}",
                  "-e", "IMAGE_NAME=#{$image_base}",
                  $dsde_toolbox_image_name,
                  "consul-template", "-config=/etc/consul-template/config/config.json",
                  "-template=#{file_name}:#{output_file_name}",
                  "-once"
    ]
  else
    vault_cmd = ["consul-template", "-config=/etc/consul-template/config/config.json",
                              "-template=#{file_name}:#{output_file_name}", "-once"]
  end

  Open3.popen3(*vault_cmd) { |stdin, stdout, stderr, wait_thread|
    if wait_thread.value.success?
      puts "#{file_name} > #{output_file_name}"
      File.delete(file_name)
    else
      puts stderr.read
      $failure_rendering = true
      $failed_to_render_file_names.push(file_name)
    end
  }
end

def render(file_name, output_file_name = nil, custom_path = nil)
  if custom_path
    path = "#{custom_path}/#{file_name}"
  else
    path = "#{file_name}"
  end
  render_from_path(path, output_file_name)
end

def base64decode(input_file_name, output_file_name)
  File.write(output_file_name, Base64.decode64(File.read(input_file_name)))
  puts "#{input_file_name} > #{output_file_name}"
  File.delete(input_file_name)
end

puts "Creating pepper configuration for\n  #{$env}/#{$version}\ninto\n  #{output_dir}\n..."

Dir.mktmpdir(nil, Dir.pwd) {|dir|
  Dir.chdir(dir) do
    puts "Grabbing manifest..."
    manifest_name = File.basename($manifest)

    if manifest_name == "manifest.rb"
      copy_file_from_path("#{manifest_name}", nil, true)
    else
      copy_file_from_path("#{$manifest}", nil, true)
    end

    eval(File.read("#{manifest_name}"))
    File.delete("#{manifest_name}")
  end

  if File.exist?(output_dir)
    should_overwrite = false
    if $suppress_prompt
      should_overwrite = true
    else
      print "\n#{output_dir} exists.\nOverwrite with new config? (y/n): "
      STDOUT.flush
      answer = gets.chomp
      if answer == "y"
        should_overwrite = true
      end
    end
    if should_overwrite
      FileUtils.rm_rf(output_dir)
      FileUtils.cp_r("#{dir}/.", output_dir)
      puts "\n* New configuration written to #{output_dir}"
    else
      puts "New configuration discarded."
    end
  else
    FileUtils.cp_r("#{dir}/.", output_dir)
    puts "\n* New configuration written to #{output_dir}"
  end

  # if encountered a rendering failure, fail the whole script
  if $failure_rendering
    puts "ERROR REPORT! Configure failed for the following file(s)!"
    $failed_to_render_file_names.each { |x| puts x }
    exit 1
  end
}
