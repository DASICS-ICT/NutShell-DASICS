# launch synthesis
launch_runs synth_1 -jobs 20
wait_on_run synth_1

# Run implementation and generate bitstream
launch_runs impl_1 -to_step write_bitstream -jobs 20
wait_on_run impl_1

# Export hardware
write_hw_platform -fixed -force -file $project_dir/$topmodule.xsa
